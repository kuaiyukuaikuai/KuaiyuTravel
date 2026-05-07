package com.kuaiyukuaikuai.kuaiyutravel.common.listener;

import com.kuaiyukuaikuai.kuaiyutravel.common.config.RabbitMQConfig;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.Follow;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.FollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class BlogFeedListener {

    @Resource
    private FollowService followService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 只需要这一行，监听刚刚在 Config 里配置好的队列！
    @RabbitListener(queues = RabbitMQConfig.BLOG_FEED_QUEUE)
    public void listenBlogPush(Map<String, Object> message) {
        Long userId = Long.valueOf(message.get("userId").toString());
        String blogId = message.get("blogId").toString();
        
        log.info("【RabbitMQ接收端】接到推流任务，开始给用户 {} 的粉丝推送博客 {}", userId, blogId);

        // 1. 查询笔记作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
        if (follows == null || follows.isEmpty()) {
            log.info("该用户没有粉丝，无需推送");
            return;
        }

        // 2. 循环推送笔记 ID 到粉丝的 Redis 收件箱
        for (Follow follow : follows) {
            String key = "feed:" + follow.getUserId();
            stringRedisTemplate.opsForZSet().add(key, blogId, System.currentTimeMillis());
        }
        
        log.info("【RabbitMQ接收端】推送完成，共推送给 {} 个粉丝", follows.size());
    }
}