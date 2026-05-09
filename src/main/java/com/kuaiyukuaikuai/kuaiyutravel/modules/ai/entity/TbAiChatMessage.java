package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * AI 助手聊天记录明细表 (长时记忆)
 */
@Data
@Accessors(chain = true)
@TableName("tb_ai_chat_message")
public class TbAiChatMessage {

    /**
     * 主键 (明细表使用数据库自增即可)
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的会话ID (对应 tb_ai_chat_session 的 id)
     */
    private String sessionId;

    /**
     * 角色：USER / ASSISTANT / SYSTEM / TOOL
     */
    private String role;

    /**
     * 消息具体内容 (大模型回复或用户输入)
     */
    private String content;

    /**
     * 消息时间
     */
    private LocalDateTime createTime;
}