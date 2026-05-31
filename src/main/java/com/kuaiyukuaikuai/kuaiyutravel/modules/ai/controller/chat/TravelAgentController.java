package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.controller.chat;

import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.chat.TbAiChatMessage;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.chat.TbAiChatSession;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.service.chat.AiChatHistoryService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.service.chat.TravelAgentOrchestrator;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 助手接入层
 * 职责：仅负责接收请求、身份拦截、以及建立 SSE 流式连接
 */
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class TravelAgentController {

    @Resource
    private TravelAgentOrchestrator travelAgentOrchestrator;
    @Resource
    private AiChatHistoryService aiChatHistoryService;

    /**
     * 流式对话接口 (SSE)
     * 使用 Flux<String> 和 TEXT_EVENT_STREAM_VALUE 实现打字机效果
     *
     * @param message 用户输入的文本
     * @param conversationId 会话ID (前端传入，用于区分不同聊天窗口，支持记忆隔离)
     * @return Flux<String>
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(
            @RequestParam("message") String message,
            @RequestParam("conversationId") String conversationId) {

        // 1. 获取当前登录用户 ID
        Long userId = UserHolder.getUserId();

        // 2. 将复杂的大模型调度、记忆挂载、工具调用全部委托给 Orchestrator (编排层) 处理
        return travelAgentOrchestrator.streamChat(userId, conversationId, message);
    }
    /**
     * 获取当前用户的会话列表 (左侧菜单)
     */
    @GetMapping("/sessions")
    public Result getSessions() {
        Long userId = UserHolder.getUserId();
        List<TbAiChatSession> sessions = aiChatHistoryService.getUserSessions(userId);

        return Result.ok(sessions);
    }

    /**
     * 获取指定会话的历史聊天记录 (右侧主界面回显)
     */
    @GetMapping("/sessions/{conversationId}/messages")
    public Result getMessages(@PathVariable("conversationId") String conversationId) {
        Long userId = UserHolder.getUserId();
        List<TbAiChatMessage> messages = aiChatHistoryService.getSessionMessages(conversationId, userId);

        return Result.ok(messages);
    }

    /**
     * 删除历史会话
     */
    @DeleteMapping("/sessions/{conversationId}")
    public Result deleteSession(@PathVariable("conversationId") String conversationId) {
        Long userId = UserHolder.getUserId();
        aiChatHistoryService.deleteSession(conversationId, userId);

        return Result.ok();
    }
}