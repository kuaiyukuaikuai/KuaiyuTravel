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

@Slf4j
@Component
public class VoucherOrderListener {

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private VoucherOrderService voucherOrderService; // 直接注入 Service

    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void listenSeckillOrder(VoucherOrder voucherOrder) {
        log.info("接收到秒杀订单消息，开始异步入库，订单ID：{}", voucherOrder.getId());
        
        Long userId = voucherOrder.getUserId();
        // 给用户加锁，防止极端情况下的并发兜底
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }
        try {
            // 直接调用，Spring 会自动处理 @Transactional 事务，不用再调 proxy 了！
            voucherOrderService.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }
}