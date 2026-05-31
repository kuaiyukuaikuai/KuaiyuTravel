package com.kuaiyukuaikuai.kuaiyutravel.modules.my.service;

import com.kuaiyukuaikuai.kuaiyutravel.modules.my.dto.LoginFormDTO;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.vo.UserDTO;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.dto.UserPasswordDTO;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.dto.UserUpdateDTO;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpSession;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 发送验证码
     * @param phone 手机号
     * @param session 会话
     */
    void sendCode(String phone, HttpSession session);

    /**
     * 登录
     * @param loginForm 登录表单
     * @param session 会话
     * @return 登录成功的 token
     */
    String login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 退出登录
     * @param token 令牌
     */
    void logout(String token);

    /**
     * 签到
     */
    void sign();

    /**
     * 获取签到次数
     * @return 签到次数
     */
    Integer signCount();

    /**
     * 更新用户信息
     * @param updateDTO 更新信息
     */
    void updateUserInfo(UserUpdateDTO updateDTO);

    /**
     * 更新密码
     * @param passwordDTO 密码信息
     */
    void updatePassword(UserPasswordDTO passwordDTO);

    /**
     * 密码登录
     * @param loginForm 登录表单
     * @return 登录成功的 token
     */
    String loginByPassword(LoginFormDTO loginForm);

    /**
     * 根据ID获取用户DTO
     * @param userId 用户ID
     * @return 用户DTO
     */
    UserDTO getUserDTOById(Long userId);
}
