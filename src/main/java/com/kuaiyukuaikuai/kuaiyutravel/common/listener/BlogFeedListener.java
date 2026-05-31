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

/**
 * 博客 Feed 流推送监听器，基于 RabbitMQ 实现异步粉丝推送。
 *
 * <p>接收博客发布事件，将博客 ID 推送到所有粉丝的 Redis 收件箱（ZSet 结构，按时间排序）。</p>
 */
@Slf4j
@Component
public class BlogFeedListener {

    @Resource
    private FollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 监听博客 Feed 队列，将博客推送到作者粉丝的 Redis 收件箱。
     *
     * @param message 包含 userId（作者ID）和 blogId（博客ID）的消息体
     */
    @RabbitListener(queues = RabbitMQConfig.BLOG_FEED_QUEUE)
    public void listenBlogPush(Map<String, Object> message) {
        Long userId = Long.valueOf(message.get("userId").toString());
        String blogId = message.get("blogId").toString();

        log.info("接收到推流任务，开始给用户 {} 的粉丝推送博客 {}", userId, blogId);

        // 1. 查询笔记作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
        if (follows == null || follows.isEmpty()) {
            log.info("该用户没有粉丝，无需推送");
            return;
        }

        // 2. 将博客 ID 推送到每个粉丝的 Redis 收件箱
        for (Follow follow : follows) {
            String key = "feed:" + follow.getUserId();
            stringRedisTemplate.opsForZSet().add(key, blogId, System.currentTimeMillis());
        }

        log.info("推送完成，共推送给 {} 个粉丝", follows.size());
    }
}
