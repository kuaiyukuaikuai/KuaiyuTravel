package com.kuaiyukuaikuai.kuaiyutravel.controller;

import cn.hutool.core.bean.BeanUtil;
import com.kuaiyukuaikuai.kuaiyutravel.dto.*;
import com.kuaiyukuaikuai.kuaiyutravel.entity.User;
import com.kuaiyukuaikuai.kuaiyutravel.entity.UserInfo;
import com.kuaiyukuaikuai.kuaiyutravel.service.BlogService;
import com.kuaiyukuaikuai.kuaiyutravel.service.UserInfoService;
import com.kuaiyukuaikuai.kuaiyutravel.service.UserService;
import com.kuaiyukuaikuai.kuaiyutravel.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;

/**
 * 用户控制器
 * 处理用户相关的请求
 * 
 * @author 快鱼
 * @since 2026-04-17
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private UserInfoService userInfoService;

    @Resource
    private BlogService blogService;

    /**
     * 发送手机验证码
     * 
     * @param phone 手机号
     * @param session 会话
     * @return 发送结果
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * 
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     * @param session 会话
     * @return 登录结果
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * 
     * @param request 请求
     * @return 登出结果
     */
    @PostMapping("/logout")
    public Result logout(jakarta.servlet.http.HttpServletRequest request) {
        // 1. 从请求头获取token
        String token = request.getHeader("authorization");
        if (cn.hutool.core.util.StrUtil.isBlank(token)) {
            return Result.fail("未登录");
        }
        
        // 2. 调用userService执行登出逻辑
        return userService.logout(token);
    }

    /**
     * 获取当前登录用户信息
     * 
     * @return 用户信息
     */
    @GetMapping("/me")
    public Result me() {
        // 获取当前登录的用户并返回
        return Result.ok(UserHolder.getUser());
    }

    /**
     * 获取用户详情
     * 
     * @param userId 用户id
     * @return 用户详情
     */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        return userInfoService.getUserInfoDetail(userId);
    }

    /**
     * 根据id查询用户
     * 
     * @param userId 用户id
     * @return 用户信息
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    /**
     * 签到功能
     * 
     * @return 签到结果
     */
    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }

    /**
     * 统计签到功能
     * 
     * @return 签到统计结果
     */
    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }

    /**
     * 修改用户基本资料 (昵称、头像)
     * 
     * @param updateDTO 用户更新信息
     * @return 更新结果
     */
    @PutMapping("/info")
    public Result updateUserInfo(@RequestBody UserUpdateDTO updateDTO) {
        // 交给 Service 层处理具体逻辑
        return userService.updateUserInfo(updateDTO);
    }

    /**
     * 修改密码
     * 
     * @param passwordDTO 密码更新信息
     * @return 更新结果
     */
    @PutMapping("/password")
    public Result updatePassword(@RequestBody UserPasswordDTO passwordDTO) {
        return userService.updatePassword(passwordDTO);
    }

    /**
     * 密码登录功能
     * 
     * @param loginForm 登录参数
     * @return 登录结果
     */
    @PostMapping("/login/password")
    public Result loginByPassword(@RequestBody LoginFormDTO loginForm) {
        // 将具体的业务逻辑交给 Service 层处理
        return userService.loginByPassword(loginForm);
    }
}