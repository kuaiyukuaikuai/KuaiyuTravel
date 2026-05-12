package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.*;

/**
 * 快鱼旅行 AI 核心配置类
 */
@Configuration
public class TravelAiConfig {


    // 读取测试用的低级模型名称，如果配置缺失则默认使用 V3
    @Value("${spring.ai.openai.chat.low-level-model:deepseek-ai/DeepSeek-V3}")
    private String lowLevelModel;
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

    /**
     * 实例2：测试数据生成器专用 AI (强制覆盖为低级快速模型 V3)
     */
    @Bean(name = "generatorChatClient")
    public ChatClient generatorChatClient(ChatClient.Builder builder) {
        return builder
                // 核心点：Spring AI 2.0.0-M4 版本的 Builder 去掉了 with 前缀
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(lowLevelModel)       // 把 withModel 改成 model
                        .temperature(1.0)           // 把 withTemperature 改成 temperature
                        .build())
                .build();
    }

    /**
     * AI 专属并发线程池 (又快又稳的黄金交叉点配置)
     */
    @Bean(name = "aiThreadPool")
    public ExecutorService aiThreadPool() {
        return new ThreadPoolExecutor(
                20,    // 核心线程数：5（保持5个并发“细水长流”地稳定输出，刚好压在TPM红线边缘）
                40,    // 最大线程数：8（给网络波动的任务一点缓冲空间，不宜过大）
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(8000), // 队列放开到 5000，让主线程可以一次性把几千个地点全部装进去
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy() // 队列满了主线程自己上，绝对不丢数据
        );
    }
}