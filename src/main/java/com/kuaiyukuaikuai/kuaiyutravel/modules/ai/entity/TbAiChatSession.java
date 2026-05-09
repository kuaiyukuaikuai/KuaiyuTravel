package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * AI 助手聊天会话表
 */
@Data
@Accessors(chain = true) // 开启链式编程，方便快速 set 值
@TableName("tb_ai_chat_session")
public class TbAiChatSession {

    /**
     * 会话ID (由前端生成并传入的 conversationId)
     * 注意：这里必须是 INPUT 策略，不能是自增或雪花算法
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 会话摘要/标题
     */
    private String topic;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}