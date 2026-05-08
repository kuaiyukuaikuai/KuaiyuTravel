package com.kuaiyukuaikuai.kuaiyutravel.common.aspect;

import com.kuaiyukuaikuai.kuaiyutravel.common.annotation.RateLimit;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.vo.UserDTO;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class RateLimitAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 获取当前用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }

        // 构造 Key：前缀 + 方法名 + 用户ID
        String key = rateLimit.prefix() + joinPoint.getSignature().getName() + ":" + user.getId();

        // 利用 SETNX 限流
        Boolean isAllowed = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", rateLimit.time(), TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(isAllowed)) {
            // 触发限流，直接返回统一定义的错误信息
            return Result.fail(rateLimit.msg());
        }

        // 放行，执行原方法
        return joinPoint.proceed();
    }
}