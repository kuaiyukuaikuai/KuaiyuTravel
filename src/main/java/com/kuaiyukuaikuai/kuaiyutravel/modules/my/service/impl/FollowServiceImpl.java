package com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.dto.UserDTO;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.Follow;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.UserInfo;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.mapper.FollowMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.FollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.UserInfoService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.UserService;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.SystemConstants;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注服务实现类
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserService userService;
    @Resource
    private UserInfoService userInfoService;
    
    /**
     * 关注或取消关注
     * @param followUserId 被关注用户ID
     * @param isFollow 是否关注
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result follow(Long followUserId, Boolean isFollow) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        Long userId = user.getId();
        // 1.判断关注还是取关

        if (isFollow) {
            // 2.关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // 把关注用户id保存到redis的set集合 sadd userId followUserId
                stringRedisTemplate.opsForSet().add("follows:" + userId, followUserId.toString());
                // 更新关注数和粉丝数
                checkAndInitUserInfo(userId);
                checkAndInitUserInfo(followUserId);
                userInfoService.update().setSql("followee = followee + 1").eq("user_id", userId).update();
                userInfoService.update().setSql("fans = fans + 1").eq("user_id", followUserId).update();
            }
        } else {
            // 3.取关，删除数据
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            // 把关注用户id从redis的set集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove("follows:" + userId, followUserId.toString());
                // 更新关注数和粉丝数（防止负数）
                checkAndInitUserInfo(userId);
                checkAndInitUserInfo(followUserId);
                userInfoService.update().setSql("followee = followee - 1").eq("user_id", userId).gt("followee", 0).update();
                userInfoService.update().setSql("fans = fans - 1").eq("user_id", followUserId).gt("fans", 0).update();
            }
        }
        return Result.ok();
    }

    /**
     * 检查并初始化用户信息记录
     * @param id 用户ID
     */
    private void checkAndInitUserInfo(Long id) {
        UserInfo userInfo = userInfoService.getById(id);
        if (userInfo == null) {
            userInfo = new UserInfo();
            userInfo.setUserId(id);
            userInfo.setFans(0);
            userInfo.setFollowee(0);
            userInfoService.save(userInfo);
        }
    }

    /**
     * 查询是否关注
     * @param followUserId 被关注用户ID
     * @return 关注状态
     */
    @Override
    public Result isFollow(Long followUserId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        Long userId = user.getId();
        Long count = query()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    /**
     * 获取共同关注
     * @param id 用户ID
     * @return 共同关注用户列表
     */
    @Override
    public Result followCommons(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        Long userId = user.getId();
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect("follows:" + id, "follows:" + userId);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(u -> BeanUtil.copyProperties(u,UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    /**
     * 查询粉丝列表
     * @param id 用户ID
     * @param current 当前页码
     * @return 粉丝列表
     */
    @Override
    public Result queryFans(Long id, Integer current) {
        Page<Follow> page = query()
                .eq("follow_user_id", id)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        
        List<Follow> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        
        List<UserDTO> userDTOList = records.stream()
                .map(follow -> userService.getUserDTOById(follow.getUserId()))
                .collect(Collectors.toList());
        
        return Result.ok(userDTOList);
    }

    /**
     * 查询关注列表
     * @param id 用户ID
     * @param current 当前页码
     * @return 关注列表
     */
    @Override
    public Result queryFollowings(Long id, Integer current) {
        Page<Follow> page = query()
                .eq("user_id", id)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        
        List<Follow> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        
        List<UserDTO> userDTOList = records.stream()
                .map(follow -> userService.getUserDTOById(follow.getFollowUserId()))
                .collect(Collectors.toList());
        
        return Result.ok(userDTOList);
    }
}