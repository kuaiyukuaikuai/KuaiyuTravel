package com.kuaiyukuaikuai.kuaiyutravel.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    // 从 application.yaml 中动态读取主机IP
    @Value("${spring.data.redis.host}")
    private String host;

    // 从 application.yaml 中动态读取端口
    @Value("${spring.data.redis.port}")
    private String port;

    // 从 application.yaml 中动态读取密码
    @Value("${spring.data.redis.password}")
    private String password;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 拼接完整的 redis 地址 (Redisson 要求必须以 redis:// 开头)
        String address = "redis://" + host + ":" + port;

        config.useSingleServer()
                .setAddress(address)
                .setPassword(password); // 设置密码

        return Redisson.create(config);
    }
}