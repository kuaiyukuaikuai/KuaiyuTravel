package com.kuaiyukuaikuai.kuaiyutravel.common.listener;

import com.kuaiyukuaikuai.kuaiyutravel.common.config.RabbitMQConfig;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.VoucherOrder;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.VoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单 MQ 监听器，处理异步订单入库。
 *
 * <p>通过分布式锁防止极端并发场景下的重复下单。</p>
 */
@Slf4j
@Component
public class VoucherOrderListener {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private VoucherOrderService voucherOrderService;

    /**
     * 监听秒杀订单队列，异步入库订单数据。
     *
     * <p>使用 Redisson 分布式锁对用户维度加锁，避免并发重复下单。
     * 直接调用 Service 方法，Spring 自动处理 {@code @Transactional} 事务。</p>
     *
     * @param voucherOrder 秒杀订单实体
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void listenSeckillOrder(VoucherOrder voucherOrder) {
        log.info("接收到秒杀订单消息，开始异步入库，订单ID：{}", voucherOrder.getId());

        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 锁粒度细化到用户+优惠券维度，避免用户同时秒多个券时相互阻塞
        String lockKey = "lock:order:" + userId + ":" + voucherId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock = false;
        try {
            isLock = lock.tryLock(3, 30, TimeUnit.SECONDS);
            if (!isLock) {
                log.warn("分布式锁竞争失败，可能为重复下单: userId={}, voucherId={}", userId, voucherId);
                // 不返回成功，让 MQ 重试（默认重试机制）
                throw new RuntimeException("分布式锁获取失败，触发 MQ 重试");
            }
            voucherOrderService.createVoucherOrder(voucherOrder);
            log.info("秒杀订单异步入库成功: orderId={}, userId={}, voucherId={}",
                    voucherOrder.getId(), userId, voucherId);
        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断: userId={}, voucherId={}", userId, voucherId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断", e);
        } catch (Exception e) {
            log.error("秒杀订单异步入库失败: orderId={}, userId={}, voucherId={}",
                    voucherOrder.getId(), userId, voucherId, e);
            throw e; // 抛出异常触发 MQ 重试或进入死信队列
        } finally {
            if (isLock) {
                lock.unlock();
            }
        }
    }
}
