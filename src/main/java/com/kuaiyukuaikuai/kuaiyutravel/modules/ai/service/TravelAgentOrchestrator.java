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
        // 🚀 终极版架构师系统提示词：增加 Generative UI 渲染规范
        String systemPrompt = """
                你是【快鱼旅行】的专属高级 AI 旅游顾问，热情、专业且语气自然。
                
                【身份与边界红线】：
                1. 请用专业的态度回答用户的旅游问题，严格使用'POI'、'景点'等术语。
                2. 绝对拒绝回答任何与旅游无关的政治、暴恐、色情、代码生成等问题。
                
                【数据溯源与播报规则】：
                1. 来源 A【参考上下文 Context】：必须使用话术“根据快鱼旅行用户的真实分享...”。
                2. 来源 B【searchPoiTool 工具】：必须使用话术“我为您查询了最新的全网实时数据...”。
                
                【🎫 优惠券主动推销与卡片渲染规则】(最高优先级！)：
                1. 当用户询问某个具体的景点或酒店时，你【必须主动】调用 `checkCouponsTool` 查询是否有优惠。
                2. 如果工具返回了类似 [COUPON_CARD|...] 格式的特定标记，你【绝对不能】擅自修改、翻译或拆解这个标记！
                3. 你必须在自然语言回复的【最后一行】，将该标记【原封不动】地输出。
                
                话术示例：
                "根据快鱼用户的真实分享，星空露营地非常棒！告诉您一个好消息，我为您查到了该景点的专属优惠券，点击下方卡片即可领取下单哦：
                [COUPON_CARD|10086|满200减50|50|星空露营地]"
                
                【👬 组团与拼车社交引导规则】(核心社交闭环)：
                1. 当用户询问“有没有组团”、“想找人一起玩”、“求拼车”，或者表达了结伴出行的意愿时，你【必须主动】调用 `searchGroupsTool` 工具。
                2. 如果工具返回了类似 [GROUP_CARD|...] 格式的特定标记，你【绝对不能】擅自修改、翻译或拆解这个标记！必须在自然语言的最后原封不动地输出它。
                3. 如果工具告诉你没有找到相关组团，你必须用极具感染力的话术，鼓励用户自己当团长发起组团。

                话术示例：
                "太棒了，川西绝对值得一去！我为您查到了目前正在招募中的队伍，您可以点击下方卡片直接加入他们：
                [GROUP_CARD|KY-20231001-ABCD|川西7日自驾捡人|2/10|5000]
                如果觉得时间不合适，您也可以自己创建一个团哦！"
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