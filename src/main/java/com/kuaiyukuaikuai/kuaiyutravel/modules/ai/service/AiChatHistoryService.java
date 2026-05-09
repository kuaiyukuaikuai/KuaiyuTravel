package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.service;

import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.TbAiChatMessage;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.TbAiChatSession;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.mapper.TbAiChatMessageMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.mapper.TbAiChatSessionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiChatHistoryService {

    @Resource
    private TbAiChatSessionMapper sessionMapper;

    @Resource
    private TbAiChatMessageMapper messageMapper;

    /**
     * 异步保存单条聊天记录
     * 面试话术点：使用 @Async 彻底剥离主线程，绝对不影响流式 SSE 的响应速度
     */
    @Async
    public void asyncSaveMessage(String conversationId, Long userId, String role, String content) {
        try {
            // 1. 检查是否存在该会话，不存在则初始化 (使用 selectById 极速查询)
            TbAiChatSession session = sessionMapper.selectById(conversationId);
            if (session == null) {
                session = new TbAiChatSession()
                        .setId(conversationId)
                        .setUserId(userId)
                        .setTopic("新的旅行规划"); // 后期可以调用大模型自动生成摘要，现在先写死
                sessionMapper.insert(session);
            }

            // 2. 插入聊天明细记录
            TbAiChatMessage message = new TbAiChatMessage()
                    .setSessionId(conversationId)
                    .setRole(role)
                    .setContent(content);
            messageMapper.insert(message);

            log.info("异步落盘成功 -> 会话: [{}], 角色: [{}]", conversationId, role);
        } catch (Exception e) {
            // 异常捕获非常重要，保证底层数据异常不会中断用户正在进行的聊天
            log.error("聊天记录持久化失败, conversationId: {}", conversationId, e);
        }
    }
}