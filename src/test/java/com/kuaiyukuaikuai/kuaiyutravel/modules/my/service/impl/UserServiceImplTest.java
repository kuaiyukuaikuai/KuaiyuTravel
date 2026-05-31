package com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.impl;

import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.dto.LoginFormDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户服务单元测试 - 纯业务逻辑测试（不依赖Spring上下文）
 */
class UserServiceImplTest {

    @Test
    void login_ShouldThrowException_WhenPhoneInvalid() {
        // Given
        LoginFormDTO loginForm = new LoginFormDTO();
        loginForm.setPhone("123"); // 无效手机号
        loginForm.setCode("123456");

        UserServiceImpl userService = new UserServiceImpl();

        // Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.login(loginForm, null));
        assertEquals(ErrorCode.USER_PHONE_INVALID.getCode(), exception.getCode());
        assertEquals("手机号格式错误！", exception.getMessage());
    }

    @Test
    void login_ShouldThrowException_WhenPhoneNull() {
        // Given
        LoginFormDTO loginForm = new LoginFormDTO();
        loginForm.setPhone(null);
        loginForm.setCode("123456");

        UserServiceImpl userService = new UserServiceImpl();

        // Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.login(loginForm, null));
        assertEquals(ErrorCode.USER_PHONE_INVALID.getCode(), exception.getCode());
    }
}
