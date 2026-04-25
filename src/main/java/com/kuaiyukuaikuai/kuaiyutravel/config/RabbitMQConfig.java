package com.kuaiyukuaikuai.kuaiyutravel.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
// 👇 1. 导包换成没有 2 的新类
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    // 交换机名称
    public static final String SECKILL_EXCHANGE = "seckill.direct";
    // 队列名称
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    // 路由键
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

    @Bean
    public MessageConverter jsonMessageConverter() {
        // 👇 2. 实例化也换成没有 2 的新类
        return new JacksonJsonMessageConverter();
    }
}