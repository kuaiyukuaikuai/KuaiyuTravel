package com.kuaiyukuaikuai.kuaiyutravel.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        return new JacksonJsonMessageConverter();
    }
}