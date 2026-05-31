package com.kuaiyukuaikuai.kuaiyutravel.common.aspect;

import com.kuaiyukuaikuai.kuaiyutravel.common.annotation.RateLimit;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.vo.UserDTO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class RateLimitAspect {

    @Resource
    private RedissonClient redissonClient;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        UserDTO user = UserHolder.getUserOrNull();
        String clientId;
        if (user != null) {
            clientId = user.getId().toString();
        } else {
            // 未登录用户使用 IP + User-Agent 哈希作为标识，避免所有未登录用户共享一个限流器
            clientId = getAnonymousClientId();
        }

        // 构造 Key：前缀 + 方法名 + 客户端标识
        String key = rateLimit.prefix() + joinPoint.getSignature().getName() + ":" + clientId;

        // 使用 Redisson 令牌桶限流
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        // 每个时间窗口允许 1 个请求
        boolean isNew = rateLimiter.trySetRate(RateType.OVERALL, 1, rateLimit.time(), RateIntervalUnit.SECONDS);
        if (isNew) {
            // 新创建的限流器设置过期时间，避免 Redis Key 永久累积
            rateLimiter.expireAsync(2 * rateLimit.time(), TimeUnit.SECONDS);
        }

        if (!rateLimiter.tryAcquire()) {
            throw new BusinessException(ErrorCode.SYSTEM_RATE_LIMIT, rateLimit.msg());
        }

        // 放行，执行原方法
        return joinPoint.proceed();
    }

    /**
     * 获取未登录用户的匿名标识（基于 IP + User-Agent）。
     */
    private String getAnonymousClientId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getRemoteAddr();
                String ua = request.getHeader("User-Agent");
                return "anon:" + Math.abs((ip + ua).hashCode());
            }
        } catch (Exception e) {
            // 兜底：返回固定值
        }
        return "anon:default";
    }
}