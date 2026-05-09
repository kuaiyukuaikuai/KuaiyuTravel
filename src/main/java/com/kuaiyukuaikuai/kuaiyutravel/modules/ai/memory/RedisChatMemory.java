package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.kuaiyukuaikuai.kuaiyutravel.common.utils.RedisConstants.*;

/**
 * 企业级 Redis 短时记忆实现
 * 适配 Spring AI 最新版单参数 get 接口
 */
@Slf4j
@Component
public class RedisChatMemory implements ChatMemory {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    private static final long TTL_HOURS = 4;

    //在内部定义窗口大小，保证 context 不会无限膨胀导致 Token 爆炸
    private static final int DEFAULT_LAST_N = 10;

    @Override
    public void add(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        String key = REDIS_KEY_PREFIX + conversationId;

        List<String> jsonMessages = messages.stream()
                .map(m -> {
                    try {
                        // 封装类型信息，解决 Jackson 反序列化接口实现类的问题
                        String type = m.getMessageType().name();
                        String payload = objectMapper.writeValueAsString(m);
                        return objectMapper.writeValueAsString(new MessageWrapper(type, payload));
                    } catch (JsonProcessingException e) {
                        log.error("序列化AI消息失败", e);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        if (!jsonMessages.isEmpty()) {
            stringRedisTemplate.opsForList().rightPushAll(key, jsonMessages);
            stringRedisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
        }
    }


    @Override
    public List<Message> get(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        String key = REDIS_KEY_PREFIX + conversationId;

        // 获取最近的 N 条记录 (从列表末尾往前取)
        List<String> jsonMessages = stringRedisTemplate.opsForList().range(key, -DEFAULT_LAST_N, -1);

        if (jsonMessages == null || jsonMessages.isEmpty()) {
            return new ArrayList<>();
        }

        return jsonMessages.stream().map(json -> {
            try {
                MessageWrapper wrapper = objectMapper.readValue(json, MessageWrapper.class);
                String type = wrapper.getType();
                String payload = wrapper.getPayload();

                // 手动解析 payload，解决 UserMessage/AssistantMessage 没有无参构造函数的问题
                JsonNode node = objectMapper.readTree(payload);
                String text = node.has("text") ? node.get("text").asText() :
                        (node.has("content") ? node.get("content").asText() : "");

                return switch (type) {
                    case "USER" -> new UserMessage(text);
                    case "ASSISTANT" -> new AssistantMessage(text);
                    case "SYSTEM" -> new SystemMessage(text);
                    case "TOOL" -> objectMapper.readValue(payload, ToolResponseMessage.class);
                    default -> null;
                };
            } catch (Exception e) {
                log.error("解析记忆消息失败: {}", json, e);
                return null;
            }
        }).filter(java.util.Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public void clear(String conversationId) {
        stringRedisTemplate.delete(REDIS_KEY_PREFIX + conversationId);
    }

    @Data
    public static class MessageWrapper {
        private String type;
        private String payload;
        public MessageWrapper() {}
        public MessageWrapper(String type, String payload) {
            this.type = type;
            this.payload = payload;
        }
    }
}