package com.kuaiyukuaikuai.kuaiyutravel.common.utils;

import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.vo.UserDTO;

public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        UserDTO user = tl.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long getUserId() {
        return getUser().getId();
    }

    /**
     * 获取当前用户（可能为 null，用于非强制登录场景）。
     */
    public static UserDTO getUserOrNull() {
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
