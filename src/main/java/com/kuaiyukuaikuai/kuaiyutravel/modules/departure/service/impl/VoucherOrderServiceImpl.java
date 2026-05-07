package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.common.config.RabbitMQConfig;
import com.kuaiyukuaikuai.kuaiyutravel.modules.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Voucher;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.VoucherOrder;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.mapper.VoucherOrderMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.SeckillVoucherService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.VoucherOrderService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.VoucherService;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.RedisIdWorker;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 优惠券订单服务实现类
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {

    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private SeckillVoucherService seckillVoucherService;
    @Resource
    private VoucherService voucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    // 保留 Lua 脚本，用于第一步拦截请求和预扣库存
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 创建订单 (供 RabbitMQ Listener 异步调用)
     * @param voucherOrder 订单信息
     */
    @Override // 确保你在 VoucherOrderService 接口中定义了这个方法
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("用户已经购买过一次！");
            return;
        }

        // 扣减数据库库存
        boolean success = seckillVoucherService.update()
                .setSql("stock= stock -1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足！");
            return;
        }
        save(voucherOrder);
    }

    /**
     * 秒杀优惠券 (入口方法，仅负责 Redis 校验和发送 MQ 消息)
     * @param voucherId 优惠券ID
     * @return 操作结果
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        // 1. 执行 lua 脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        // 2. 判断结果为0
        if (result.intValue() != 0) {
            return Result.fail(result.intValue() == 1 ? "库存不足" : "不能重复下单");
        }

        // 3. 封装订单数据，丢入 RabbitMQ 进行异步处理！
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ROUTING_KEY,
                voucherOrder
        );

        return Result.ok(orderId);
    }

    /**
     * 普通优惠券
     */
    @Override
    @Transactional
    public Result commonVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        String lockKey = "lock:order:" + userId + ":" + voucherId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLocked = false;

        try {
            isLocked = lock.tryLock(10, TimeUnit.SECONDS);
            if (!isLocked) {
                return Result.fail("系统繁忙，请稍后再试");
            }
            Voucher voucher = voucherService.getById(voucherId);
            if (voucher == null) {
                return Result.fail("优惠券不存在！");
            }
            Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("您已经购买过该券，不可重复购买！");
            }
            VoucherOrder voucherOrder = new VoucherOrder();
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            this.save(voucherOrder);

            return Result.ok(orderId);
        } catch (InterruptedException e) {
            log.error("获取锁中断", e);
            return Result.fail("系统繁忙，请稍后再试");
        } finally {
            if (isLocked) {
                lock.unlock();
            }
        }
    }
}