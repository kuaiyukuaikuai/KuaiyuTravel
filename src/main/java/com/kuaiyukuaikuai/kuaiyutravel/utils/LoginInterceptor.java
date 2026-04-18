package com.kuaiyukuaikuai.kuaiyutravel.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.判断是否需要拦截(ThreadLocal中是否有用户)
        if (UserHolder.getUser() == null) {
            // 没有用户，拦截
            response.setStatus(401); // 拦截，返回401
            return false;
        }
        //2.存在：放行
        return true;

    }
}
