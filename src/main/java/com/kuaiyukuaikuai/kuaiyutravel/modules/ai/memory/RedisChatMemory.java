package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 企业级 Redis 短时记忆实现 (支持 Function Calling 和 Context 截断)
 */
@Component
public class RedisChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisChatMemory.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    private static final String REDIS_KEY_PREFIX = "kuaiyu:ai:session:";
    private static final long TTL_HOURS = 4;

    // 限制单次会话最多保留的消息条数（防止 Token 爆炸）
    private static final int MAX_MESSAGES_KEEP = 30;

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String key = REDIS_KEY_PREFIX + conversationId;

        List<String> jsonMessages = messages.stream()
                .map(this::serializeMessage)
                .collect(Collectors.toList());

        // 1. 批量追加到 Redis List 的右侧
        stringRedisTemplate.opsForList().rightPushAll(key, jsonMessages);

        // 2. 截断 List，仅保留最近的 MAX_MESSAGES_KEEP 条（从右往左保留）
        stringRedisTemplate.opsForList().trim(key, -MAX_MESSAGES_KEEP, -1);

        // 3. 刷新过期时间
        stringRedisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
    }

    @Override
    public List<Message> get(String conversationId) {
        String key = REDIS_KEY_PREFIX + conversationId;
        List<String> jsonMessages = stringRedisTemplate.opsForList().range(key, 0, -1);

        if (jsonMessages == null || jsonMessages.isEmpty()) {
            return new ArrayList<>();
        }

        return jsonMessages.stream()
                .map(this::deserializeMessage)
                .filter(msg -> msg != null)
                .collect(Collectors.toList());
    }

    @Override
    public void clear(String conversationId) {
        stringRedisTemplate.delete(REDIS_KEY_PREFIX + conversationId);
    }

    /**
     * 【核心修改】利用 Spring AI 内置支持或 Jackson 的全量序列化
     * 不要手动拼装 Map，因为 ToolCall, ToolResponseMessage 等结构非常复杂
     */
    private String serializeMessage(Message message) {
        try {
            // 将整个 Message 对象及其多态类型序列化
            // (要求你的 ObjectMapper 配置了多态支持，或者直接将类名写入)
            // 这里我们采用包裹一层包装类的方式确保类型安全
            MessageWrapper wrapper = new MessageWrapper(message.getMessageType().name(), objectMapper.writeValueAsString(message));
            return objectMapper.writeValueAsString(wrapper);
        } catch (JsonProcessingException e) {
            log.error("AI 消息序列化失败", e);
            throw new RuntimeException("序列化消息失败", e);
        }
    }

    private Message deserializeMessage(String json) {
        try {
            MessageWrapper wrapper = objectMapper.readValue(json, MessageWrapper.class);
            String type = wrapper.getType();
            String payload = wrapper.getPayload();

            return switch (type) {
                case "USER" -> objectMapper.readValue(payload, UserMessage.class);
                case "ASSISTANT" -> objectMapper.readValue(payload, AssistantMessage.class);
                case "SYSTEM" -> objectMapper.readValue(payload, SystemMessage.class);
                // 必须支持工具返回消息类型
                case "TOOL" -> objectMapper.readValue(payload, ToolResponseMessage.class);
                default -> {
                    log.warn("未知的AI消息类型: {}", type);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.error("AI 消息反序列化失败, 数据: {}", json, e);
            return null;
        }
    }

    /**
     * 内部包装类，用于存储原始类型和具体的 JSON 载荷
     */
    @Data
    public static class MessageWrapper {
        private String type;
        private String payload;

        public MessageWrapper() {}

        public MessageWrapper(String type, String payload) {
            this.type = type;
            this.payload = payload;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
    }
}