package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.chat.TbAiChatMessage;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.chat.TbAiChatSession;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.mapper.TbAiChatMessageMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.mapper.TbAiChatSessionMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.memory.RedisChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 聊天历史记录服务，管理会话与消息的持久化。
 *
 * <p>提供聊天记录的异步保存、会话列表查询、消息明细查询及会话删除功能。</p>
 */
@Slf4j
@Service
public class AiChatHistoryService {

    @Resource
    private TbAiChatSessionMapper sessionMapper;

    @Resource
    private TbAiChatMessageMapper messageMapper;

    @Resource
    private RedisChatMemory redisChatMemory;

    /**
     * 异步保存单条聊天记录。
     *
     * <p>使用 {@code @Async} 将持久化操作剥离主线程，避免阻塞流式 SSE 响应。</p>
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param role           消息角色（user / assistant）
     * @param content        消息内容
     */
    @Async
    public void asyncSaveMessage(String conversationId, Long userId, String role, String content) {
        try {
            // 1. 检查会话是否存在，不存在则初始化
            TbAiChatSession session = sessionMapper.selectById(conversationId);
            if (session == null) {
                session = new TbAiChatSession()
                        .setId(conversationId)
                        .setUserId(userId)
                        .setTopic("新的旅行规划");
                sessionMapper.insert(session);
            }

            // 2. 插入聊天明细记录
            TbAiChatMessage message = new TbAiChatMessage()
                    .setSessionId(conversationId)
                    .setRole(role)
                    .setContent(content);
            messageMapper.insert(message);

            log.info("异步落盘成功，会话: [{}], 角色: [{}]", conversationId, role);
        } catch (Exception e) {
            // 捕获异常防止底层数据错误中断用户聊天流程
            log.error("聊天记录持久化失败, conversationId: {}", conversationId, e);
        }
    }


    /**
     * 获取当前用户的所有历史会话列表。
     *
     * <p>按更新时间降序排列，最近活跃的会话排在最前。</p>
     *
     * @param userId 当前登录用户 ID
     * @return 会话列表，按更新时间降序排列
     */
    public List<TbAiChatSession> getUserSessions(Long userId) {
        return sessionMapper.selectList(
                new LambdaQueryWrapper<TbAiChatSession>()
                        .eq(TbAiChatSession::getUserId, userId)
                        .orderByDesc(TbAiChatSession::getUpdateTime)
        );
    }

    /**
     * 获取指定会话的历史聊天记录明细。
     *
     * <p>按创建时间升序排列，还原真实对话流程。</p>
     *
     * @param conversationId 会话 ID
     * @param userId         当前用户 ID，用于越权校验
     * @return 聊天记录列表，按创建时间升序排列
     * @throws BusinessException 无权查看或会话不存在时抛出
     */
    public List<TbAiChatMessage> getSessionMessages(String conversationId, Long userId) {
        // 越权校验：防止遍历 conversationId 获取他人会话数据
        TbAiChatSession session = sessionMapper.selectById(conversationId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此会话或会话不存在");
        }

        return messageMapper.selectList(
                new LambdaQueryWrapper<TbAiChatMessage>()
                        .eq(TbAiChatMessage::getSessionId, conversationId)
                        .orderByAsc(TbAiChatMessage::getCreateTime)
        );
    }

    /**
     * 删除指定会话及其所有聊天明细。
     *
     * <p>同步清理数据库记录与 Redis 短时记忆，避免残留数据导致"幽灵记忆"。</p>
     *
     * @param conversationId 会话 ID
     * @param userId         当前用户 ID，用于越权校验
     */
    public void deleteSession(String conversationId, Long userId) {
        // 越权校验
        TbAiChatSession session = sessionMapper.selectById(conversationId);
        if (session != null && session.getUserId().equals(userId)) {
            // 删除主表
            sessionMapper.deleteById(conversationId);
            // 删除明细表
            messageMapper.delete(
                    new LambdaQueryWrapper<TbAiChatMessage>()
                            .eq(TbAiChatMessage::getSessionId, conversationId)
            );
            // 清理 Redis 短时记忆
            redisChatMemory.clear(conversationId);
        }
    }
}
