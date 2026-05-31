package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 快鱼旅行 AI 核心配置类
 */
@Configuration
public class TravelAiConfig {


    // 读取测试用的低级模型名称，如果配置缺失则默认使用 V3
    @Value("${spring.ai.openai.chat.low-level-model:deepseek-ai/DeepSeek-V3}")
    private String lowLevelModel;
    /**
     * 声明 ChatClient Bean，手动构建以适配 Spring AI 架构规范。
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
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(lowLevelModel)
                        .temperature(1.0)
                        .build())
                .build();
    }

    /**
     * AI 专属并发线程池（使用有界队列防止内存溢出，线程命名便于监控）
     */
    @Bean(name = "aiThreadPool")
    public ExecutorService aiThreadPool() {
        AtomicInteger counter = new AtomicInteger(0);
        ThreadFactory namedFactory = r -> {
            Thread t = new Thread(r, "ai-pool-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(
                10,
                20,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1000),
                namedFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
