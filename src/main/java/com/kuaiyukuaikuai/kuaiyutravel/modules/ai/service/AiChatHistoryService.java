package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.TbAiChatMessage;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.TbAiChatSession;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.mapper.TbAiChatMessageMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.mapper.TbAiChatSessionMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.memory.RedisChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

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


    /**
     * 1. 获取当前用户的所有历史会话列表
     * @param userId 当前登录用户ID
     * @return 会话列表（按更新时间倒序排列，最近聊的在最上面）
     */
    public List<TbAiChatSession> getUserSessions(Long userId) {
        return sessionMapper.selectList(
                new LambdaQueryWrapper<TbAiChatSession>()
                        .eq(TbAiChatSession::getUserId, userId)
                        // 面试细节：一定要按 updateTime 降序，这样用户刚刚回过消息的旧会话也能顶到最上面
                        .orderByDesc(TbAiChatSession::getUpdateTime)
        );
    }

    /**
     * 2. 获取指定会话的历史聊天记录明细
     * @param conversationId 会话ID
     * @param userId 当前用户ID (用于越权校验)
     * @return 聊天记录列表（按创建时间正序排列）
     */
    public List<TbAiChatMessage> getSessionMessages(String conversationId, Long userId) {
        // 安全拦截：防止黑客遍历 conversationId 偷窥别人的旅游计划
        TbAiChatSession session = sessionMapper.selectById(conversationId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new RuntimeException("无权查看此会话或会话不存在"); // 建议换成你们项目统一的业务异常
        }

        return messageMapper.selectList(
                new LambdaQueryWrapper<TbAiChatMessage>()
                        .eq(TbAiChatMessage::getSessionId, conversationId)
                        // 聊天记录必须按时间正序，还原真实的对话流
                        .orderByAsc(TbAiChatMessage::getCreateTime)
        );
    }

    /**
     * 3. 删除指定会话及其所有的聊天明细
     */
    public void deleteSession(String conversationId, Long userId) {
        // 1. 越权校验
        TbAiChatSession session = sessionMapper.selectById(conversationId);
        if (session != null && session.getUserId().equals(userId)) {
            // 2. 删除主表
            sessionMapper.deleteById(conversationId);
            // 3. 删除明细表
            messageMapper.delete(
                    new LambdaQueryWrapper<TbAiChatMessage>()
                            .eq(TbAiChatMessage::getSessionId, conversationId)
            );
            // 4. 面试高阶亮点：别忘了把 Redis 里残留的短时记忆也清掉，防止出现“幽灵记忆”
             redisChatMemory.clear(conversationId);
        }
    }
}