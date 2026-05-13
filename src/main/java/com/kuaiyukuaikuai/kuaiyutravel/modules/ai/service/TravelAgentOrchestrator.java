package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.service;

import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.config.TravelAiTools; // 🚀 引入你的工具类
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.memory.RedisChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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

    @Resource
    private VectorStore vectorStore;

    @Resource
    private TravelAiTools travelAiTools; // 🚀 注入你的本地工具组件

    public Flux<String> streamChat(Long userId, String conversationId, String prompt) {

        aiChatHistoryService.asyncSaveMessage(conversationId, userId, "USER", prompt);
        StringBuilder fullResponseBuilder = new StringBuilder();

        // 🚀 终极版架构师系统提示词：明确界定 RAG 与 Tool 的播报口径
        String systemPrompt = """
                你是【快鱼旅行】的专属高级 AI 旅游顾问，热情、专业且语气自然。
                
                【身份与边界红线】：
                1. 请用专业的态度回答用户的旅游问题，严格使用'POI'、'景点'等术语。
                2. 绝对拒绝回答任何与旅游无关的政治、暴恐、色情、代码生成等问题。
                
                【数据溯源与播报规则】(极其重要！)：
                你在回答问题时，可能会获得两种来源的数据，请严格按照以下话术向用户区分：
                
                1. 来源 A【系统提供的参考上下文 Context】：这是快鱼旅行内部用户的真实游记和评价(RAG数据)。
                   -> 当你使用这部分信息时，必须使用话术：“根据快鱼旅行用户的真实分享...” 或 “站内有用户提到...”。
                
                2. 来源 B【调用 searchPoiTool 工具返回的数据】：这是外部合作平台提供的实时客观结构化数据。
                   -> 当你使用工具返回的结果时，必须使用话术：“我为您查询了最新的全网实时数据...” 或 “根据外部合作平台的最新信息...”。
                
                3. 如果问题需要，你可以同时调用工具(获取客观列表)并结合上下文(获取主观评价)，将两者完美融合，但必须清晰界定来源。
                4. 如果两者都没有提供有用信息，请依靠你的常识回答，并说明这是通用建议。
                """;

        return chatClient.prompt()
                .system(systemPrompt)
                .user(prompt)

                // 🚀 核心修复：把工具挂载回来！让大模型拥有调用外部接口的能力
                .tools(travelAiTools)

                // 挂载内部知识库 (RAG)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(5).build())
                        .build())

                // 挂载记忆
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
                });
    }
}