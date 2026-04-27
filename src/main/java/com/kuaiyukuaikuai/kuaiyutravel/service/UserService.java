package com.kuaiyukuaikuai.kuaiyutravel.service;

import com.kuaiyukuaikuai.kuaiyutravel.dto.LoginFormDTO;
import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.dto.UserDTO;
import com.kuaiyukuaikuai.kuaiyutravel.dto.UserPasswordDTO;
import com.kuaiyukuaikuai.kuaiyutravel.dto.UserUpdateDTO;
import com.kuaiyukuaikuai.kuaiyutravel.entity.User;
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
     * @return 操作结果
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录
     * @param loginForm 登录表单
     * @param session 会话
     * @return 登录结果
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 退出登录
     * @param token 令牌
     * @return 操作结果
     */
    Result logout(String token);

    /**
     * 签到
     * @return 签到结果
     */
    Result sign();

    /**
     * 获取签到次数
     * @return 签到次数
     */
    Result signCount();

    /**
     * 更新用户信息
     * @param updateDTO 更新信息
     * @return 操作结果
     */
    Result updateUserInfo(UserUpdateDTO updateDTO);

    /**
     * 更新密码
     * @param passwordDTO 密码信息
     * @return 操作结果
     */
    Result updatePassword(UserPasswordDTO passwordDTO);

    /**
     * 密码登录
     * @param loginForm 登录表单
     * @return 登录结果
     */
    Result loginByPassword(LoginFormDTO loginForm);

    /**
     * 根据ID获取用户DTO
     * @param userId 用户ID
     * @return 用户DTO
     */
    UserDTO getUserDTOById(Long userId);
}