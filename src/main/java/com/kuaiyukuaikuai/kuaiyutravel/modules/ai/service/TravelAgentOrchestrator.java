package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.service;

import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.memory.RedisChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import jakarta.annotation.Resource;

@Service
public class TravelAgentOrchestrator {

    @Resource
    @Qualifier("agentChatClient")
    private ChatClient chatClient;
    @Resource
    private RedisChatMemory redisChatMemory;
    @Resource
    private AiChatHistoryService aiChatHistoryService;

    /**
     * 流式对话编排
     *
     * @param userId         当前用户ID
     * @param conversationId 前端生成的会话ID（保证隔离）
     * @param prompt         用户输入的问题
     * @return Flux<String>
     */
    public Flux<String> streamChat(Long userId, String conversationId, String prompt) {

        // 1. 异步将用户的提问落盘到 MySQL
        aiChatHistoryService.asyncSaveMessage(conversationId, userId, "USER", prompt);

        // 2. 用于拼接大模型流式返回的所有字符串片段
        StringBuilder fullResponseBuilder = new StringBuilder();

        // 3. 调用 Spring AI，装载 Advisor
        return chatClient.prompt()
                .user(prompt)
                // 核心修复点 1：强制使用 Builder 模式装载记忆组件（彻底抛弃 new 关键字）
                .advisors(MessageChatMemoryAdvisor.builder(redisChatMemory).build())
                // 核心修复点 2：使用最新版的常量路径注入独立会话 ID 和记忆长度
                .advisors(a -> a
                        .param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, conversationId)
                        .param("chat_memory_retrieve_size", 10)
                )
                .stream()
                .content()
                // ... 下面的 doOnNext 和 doOnComplete 保持不变 ...
                // 4. 监听每一次数据块的返回，将其拼接起来
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        fullResponseBuilder.append(chunk);
                    }
                })
                // 5. 面试终极亮点：当流输出彻底结束时（大模型打字完毕），触发钩子将完整回答异步落盘
                .doOnComplete(() -> {
                    String fullResponse = fullResponseBuilder.toString();
                    aiChatHistoryService.asyncSaveMessage(conversationId, userId, "ASSISTANT", fullResponse);
                });
    }
}