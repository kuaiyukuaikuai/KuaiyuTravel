package com.kuaiyukuaikuai.kuaiyutravel.service;

import com.kuaiyukuaikuai.kuaiyutravel.dto.LoginFormDTO;
import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.dto.UserPasswordDTO;
import com.kuaiyukuaikuai.kuaiyutravel.dto.UserUpdateDTO;
import com.kuaiyukuaikuai.kuaiyutravel.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpSession;

/**
* @author 0
* @description 针对表【tb_user】的数据库操作Service
* @createDate 2026-04-17 11:08:15
*/
public interface UserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result logout(String token);

    Result sign();

    Result signCount();

    Result updateUserInfo(UserUpdateDTO updateDTO);

    Result updatePassword(UserPasswordDTO passwordDTO);

    Result loginByPassword(LoginFormDTO loginForm);
}
