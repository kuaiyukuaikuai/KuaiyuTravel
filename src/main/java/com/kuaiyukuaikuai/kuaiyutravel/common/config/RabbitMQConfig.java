package com.kuaiyukuaikuai.kuaiyutravel.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMQConfig {

    // ================== 1. 秒杀业务相关配置 ==================
    public static final String SECKILL_EXCHANGE = "seckill.direct";
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE);
    }

    @Bean
    public Queue seckillQueue() {
        return new Queue(SECKILL_QUEUE, true); // true 表示持久化
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(SECKILL_ROUTING_KEY);
    }

    // ================== 2. 博客推送业务相关配置 (本次新增) ==================
    public static final String BLOG_EXCHANGE = "blog.direct";
    public static final String BLOG_FEED_QUEUE = "blog.feed.queue";
    public static final String BLOG_ROUTING_KEY = "feed.push";

    // 在你的 RabbitMQConfig.java 中补充这几个常量
    public static final String AI_SYNC_QUEUE = "ai.sync.blog.queue"; // AI 同步队列名称
    public static final String AI_SYNC_ROUTING_KEY = "ai.sync.blog"; // AI 同步路由键

    public static final String AI_SYNC_COMMENT_QUEUE = "ai.sync.comment.queue";
    public static final String AI_SYNC_COMMENT_ROUTING_KEY = "ai.sync.comment";

    @Bean
    public DirectExchange blogExchange() {
        return new DirectExchange(BLOG_EXCHANGE);
    }

    @Bean
    public Queue blogFeedQueue() {
        return new Queue(BLOG_FEED_QUEUE, true);
    }

    @Bean
    public Binding blogBinding() {
        return BindingBuilder.bind(blogFeedQueue()).to(blogExchange()).with(BLOG_ROUTING_KEY);
    }

    // ================== 3. 全局 JSON 序列化配置 ==================
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 自定义 RabbitTemplate，启用消息发送确认（Confirm）和路由失败回退（Return）。
     *
     * <p>生产环境必须开启，确保消息可靠投递。发送失败时记录日志，便于排查和补偿。</p>
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);

        // 启用 Confirm 模式：Broker 收到消息后异步回调
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("[MQ-CONFIRM] 消息发送失败，Broker 未确认。correlationData={}, cause={}", correlationData, cause);
            }
        });

        // 启用 Return 模式：消息无法路由到队列时回调（如路由键错误、队列未绑定）
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("[MQ-RETURN] 消息路由失败。exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
        });

        return rabbitTemplate;
    }
}