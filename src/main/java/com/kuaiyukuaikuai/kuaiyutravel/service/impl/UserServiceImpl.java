package com.kuaiyukuaikuai.kuaiyutravel.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.dto.*;
import com.kuaiyukuaikuai.kuaiyutravel.entity.User;
import com.kuaiyukuaikuai.kuaiyutravel.service.UserService;
import com.kuaiyukuaikuai.kuaiyutravel.mapper.UserMapper;
import com.kuaiyukuaikuai.kuaiyutravel.utils.RegexUtils;
import com.kuaiyukuaikuai.kuaiyutravel.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.kuaiyukuaikuai.kuaiyutravel.utils.RedisConstants.*;
import static com.kuaiyukuaikuai.kuaiyutravel.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * @author 0
 * @description 针对表【tb_user】的数据库操作Service实现
 * @createDate 2026-04-17 11:08:15
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 保存验证码到 Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);

        // 返回结果
        return Result.ok();

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3.从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 不一致，报错
            return Result.fail("验证码错误");
        }

        // 4.一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }

        // 7.保存用户信息到 redis中
        // 7.1.随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString()));
        // 7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.返回token
        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        // 1. 构建Redis中的token key
        String tokenKey = LOGIN_USER_KEY + token;
        
        // 2. 删除Redis中的用户登录信息
        stringRedisTemplate.delete(tokenKey);
        
        // 3. 清除ThreadLocal中的用户信息
        UserHolder.removeUser();
        
        log.debug("用户登出成功，token: {}", token);
        return Result.ok();
    }

    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix =now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int day = now.getDayOfMonth();
        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix =now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int day = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有签到记录, 返回的是一个十进制数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0)
        );
        if (result == null || result.size() == 0) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0){
            return Result.ok(0);
        }
        // 6.循环遍历
        // 7.让这个数字与1做与运算，得到数字的最后一个bit位
        int count = 0;
        while (true) {
            // 8.判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 8.1 如果为0，结束
                break;
            } else {
                count++;
            }
            num = num >> 1;
        }
        return Result.ok(count);

    }

    @Override
    public Result updateUserInfo(UserUpdateDTO updateDTO) {
        // 1. 从当前线程(Token)中获取当前登录用户的 ID
        Long userId = UserHolder.getUser().getId();

        // 2. 构建需要更新的 User 对象
        User user = new User();
        user.setId(userId);
        user.setNickName(updateDTO.getNickName());
        user.setIcon(updateDTO.getIcon());

        // 3. 调用 MyBatis-Plus 的更新方法 (只会更新非 null 的字段)
        updateById(user);

        return Result.ok();
    }

    @Override
    public Result updatePassword(UserPasswordDTO passwordDTO) {
        Long userId = UserHolder.getUser().getId();

        String oldPassword = passwordDTO.getOldPassword();
        String newPassword = passwordDTO.getNewPassword();

        // 1. 校验新密码不能为空
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return Result.fail("新密码不能为空");
        }

        // 2. 查询当前用户信息
        User user = getById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        String dbPassword = user.getPassword();

        // 3. 【核心逻辑分叉】
        if (dbPassword == null || dbPassword.trim().isEmpty()) {
            // 场景 A：首次设置密码
            // 直接放行，进入下方的加密更新阶段
        } else {
            // 场景 B：修改密码，必须严格校验
            if (oldPassword == null || oldPassword.trim().isEmpty()) {
                return Result.fail("原密码不能为空");
            }

            // ⚔️ 校验旧密码是否正确
            // BCrypt.checkpw(明文密码, 数据库里的密文) -> 返回 true 或 false
            if (!BCrypt.checkpw(oldPassword, dbPassword)) {
                return Result.fail("原密码输入错误");
            }

            // ⚔️ 校验新密码不能和旧密码一样
            // 如果新密码的明文，能和数据库里的旧密文匹配上，说明新旧密码一样
            if (BCrypt.checkpw(newPassword, dbPassword)) {
                return Result.fail("新密码不能与原密码相同");
            }
        }

        // 4. 对新密码进行 Bcrypt 加密 🔒
        // BCrypt.hashpw 会自动生成随机盐并进行加密，生成一条长度为 60 的密文
        String hashedNewPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());

        // 5. 更新到数据库
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setPassword(hashedNewPassword); // 存入加密后的密文

        updateById(updateUser);

//         6. (可选) 清理 Redis 中的 Token，强制踢下线重新登录
         HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
         String token = request.getHeader("authorization");
         stringRedisTemplate.delete("login:token:" + token);

        return Result.ok();
    }

    @Override
    public Result loginByPassword(LoginFormDTO loginForm) {
        // 1. 获取前端传来的账号(这里以手机号 phone 为例)和密码
        String phone = loginForm.getPhone();
        String password = loginForm.getPassword();

        // 2. 基本非空校验
        if (StrUtil.isBlank(phone) || StrUtil.isBlank(password)) {
            return Result.fail("手机号和密码不能为空");
        }

        // 3. 根据手机号去数据库查询用户
        // query() 是 MyBatis-Plus 提供的链式查询方法
        User user = query().eq("phone", phone).one();

        // 4. 如果没查到，说明该账号还没注册
        if (user == null) {
            return Result.fail("该用户不存在，请先注册");
        }

        // 5. ⚔️ 核心：校验密码 (使用 Bcrypt)
        // 注意：BCrypt.checkpw() 第一个参数是前端传的明文，第二个参数是数据库查出来的密文
        if (!BCrypt.checkpw(password, user.getPassword())) {
            return Result.fail("账号或密码错误");
        }

        // 6. 登录成功！生成随机的 Token 作为用户的“通行证”
        String token = UUID.randomUUID().toString(true);

        // 7. 保护隐私：绝对不能把包含密码的完整 User 存入 Redis，要转换成 UserDTO
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        // 将 UserDTO 对象转为 HashMap，并且把所有字段值都转成 String 类型 (防止 Redis 存储 Long 类型 ID 时报错)
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        // 8. 存入 Redis，Key 为自定义前缀 + token
        String tokenKey = "login:token:" + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        // 9. 设置 Token 的过期时间 (例如 30 分钟。用户如果不活跃，半小时后自动掉线)
        stringRedisTemplate.expire(tokenKey, 30, TimeUnit.MINUTES);

        // 10. 最终将生成的 token 返回给前端
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

}




