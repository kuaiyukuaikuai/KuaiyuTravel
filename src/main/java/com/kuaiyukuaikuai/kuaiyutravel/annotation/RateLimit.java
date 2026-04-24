package com.kuaiyukuaikuai.kuaiyutravel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    // 限流时间，默认 10 秒
    long time() default 10;
    // 限流提示语
    String msg() default "操作过于频繁，请稍后再试";
    // 限制的业务前缀，用于区分不同接口
    String prefix() default "rate_limit:";
}