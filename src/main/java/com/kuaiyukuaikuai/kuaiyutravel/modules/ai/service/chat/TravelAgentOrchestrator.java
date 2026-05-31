package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.service.chat;

import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.tools.chat.TravelAiTools;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.memory.RedisChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import jakarta.annotation.Resource;

@Slf4j
@Service
public class TravelAgentOrchestrator {

    @Resource
    @Qualifier("agentChatClient")
    private ChatClient chatClient;

    @Resource
    private RedisChatMemory redisChatMemory;

    @Resource
    private AiChatHistoryService aiChatHistoryService;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private TravelAiTools travelAiTools;

    @Value("${kuaiyu.ai.system-prompt:}")
    private String systemPrompt;

    public Flux<String> streamChat(Long userId, String conversationId, String prompt) {
        long startTime = System.currentTimeMillis();
        log.info("[AI-MONITOR] AI流式对话开始. userId={}, conversationId={}", userId, conversationId);

        aiChatHistoryService.asyncSaveMessage(conversationId, userId, "USER", prompt);
        StringBuilder fullResponseBuilder = new StringBuilder();

        return chatClient.prompt()
                .system(systemPrompt)
                .user(prompt)
                .tools(travelAiTools)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(5).build())
                        .build())
                .advisors(MessageChatMemoryAdvisor.builder(redisChatMemory).build())
                .advisors(a -> a
                        .param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, conversationId)
                        .param("chat_memory_retrieve_size", 10)
                )
                .stream()
                .content()
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        fullResponseBuilder.append(chunk);
                    }
                })
                .doOnComplete(() -> {
                    String fullResponse = fullResponseBuilder.toString();
                    aiChatHistoryService.asyncSaveMessage(conversationId, userId, "ASSISTANT", fullResponse);
                    log.info("[AI-MONITOR] AI流式对话完成. userId={}, conversationId={}, cost={}ms, responseLength={}",
                            userId, conversationId, System.currentTimeMillis() - startTime, fullResponse.length());
                })
                .onErrorResume(e -> {
                    log.error("[AI-MONITOR] AI流式对话异常. userId={}, conversationId={}, cost={}ms, error={}",
                            userId, conversationId, System.currentTimeMillis() - startTime, e.getMessage(), e);
                    return Flux.just("[ERROR]" + (e.getMessage() != null ? e.getMessage() : "AI服务暂时不可用，请稍后再试"));
                });
    }
}
