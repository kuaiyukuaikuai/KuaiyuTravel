package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 快鱼旅行 AI 核心配置类
 */
@Configuration
public class TravelAiConfig {

    /**
     * 声明 ChatClient Bean
     * 面试亮点：解释这里为什么需要我们手动用 Builder 来构建，
     * 这是适配 Spring AI 最新架构规范的最佳实践。
     *
     * @param builder Spring Boot 自动装配的 ChatClient 构建器
     * @return 构建好的 ChatClient 实例
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                // 在这里可以全局配置你的 Agent 人设（System Prompt）
                .defaultSystem("你是快鱼旅行的专属AI规划师，请用专业的态度回答用户的旅游问题，严格使用'POI'、'景点'等术语，拒绝回答与旅游无关的政治、代码等问题。")
                .build();
    }
}