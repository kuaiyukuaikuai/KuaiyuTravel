package com.kuaiyukuaikuai.kuaiyutravel.ai.controller;

import com.kuaiyukuaikuai.kuaiyutravel.ai.config.TravelAiTools;
import org.springframework.ai.chat.client.ChatClient;
// 💡 修正 1：正确的 Advisor 包路径
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
// 💡 修正 2：使用 ChatMemory 接口
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class TravelAgentController {

    private final ChatClient chatClient;
    private final TravelAiTools travelAiTools;
    private final ChatMemory chatMemory;// 记忆顾问，用于存储和检索用户聊天记录

    // 💡 修正 3：使用 Resource 读取文件内容，并且让 Spring 自动注入 ChatMemory
    public TravelAgentController(ChatClient.Builder chatClientBuilder,
                                 TravelAiTools travelAiTools,
                                 ChatMemory chatMemory,
                                 @Value("classpath:/prompts/agent-system.st") Resource systemPrompt) {

        this.travelAiTools = travelAiTools;
        this.chatMemory = chatMemory;

        // 💡 最佳实践：在系统启动时就构建好自带系统人设的 ChatClient
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        // 实际业务中，这应该是从 Token 里获取的当前登录用户的 ID。现在先写死方便测试。
        String chatId = "test-user-id";

        return chatClient.prompt()
                .user(message)
                .tools(this.travelAiTools)
                .advisors(MessageChatMemoryAdvisor.builder(this.chatMemory)
                        .conversationId(chatId)
                        .build())
                .call()
                .content();
    }
}