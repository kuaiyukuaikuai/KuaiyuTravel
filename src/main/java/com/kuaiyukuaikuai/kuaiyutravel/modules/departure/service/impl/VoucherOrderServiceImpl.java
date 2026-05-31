package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.common.config.RabbitMQConfig;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Voucher;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.VoucherOrder;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.SeckillVoucher;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.mapper.VoucherOrderMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.SeckillVoucherService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.VoucherOrderService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.VoucherService;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.RedisIdWorker;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.vo.VoucherOrderVO;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.kuaiyukuaikuai.kuaiyutravel.common.utils.RedisConstants.SECKILL_STOCK_KEY;

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
    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        try {
            // 幂等性检查：防止 MQ 重复投递导致重复创建订单
            long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                log.warn("用户重复购买，幂等拦截: userId={}, voucherId={}", userId, voucherId);
                return;
            }

            // 扣减数据库库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!success) {
                log.warn("秒杀数据库库存不足: voucherId={}", voucherId);
                // 数据库库存不足时回补 Redis 库存（最终一致性补偿）
                stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId);
                return;
            }

            save(voucherOrder);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 数据库唯一索引 (user_id, voucher_id) 拦截重复订单
            log.warn("数据库唯一索引拦截重复订单: userId={}, voucherId={}", userId, voucherId);
            // 回补已扣减的数据库库存
            seckillVoucherService.update()
                    .setSql("stock = stock + 1")
                    .eq("voucher_id", voucherId)
                    .update();
            // 回补 Redis 库存
            stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId);
        } catch (Exception e) {
            log.error("创建秒杀订单异常，触发库存回补: userId={}, voucherId={}", userId, voucherId, e);
            // 异常时回补 Redis 库存，保证 Redis 库存 >= DB 库存
            stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId);
            throw e; // 继续抛出异常触发 MQ 重试或死信
        }
    }

    /**
     * 秒杀优惠券 (入口方法，仅负责 Redis 校验和发送 MQ 消息)
     * @param voucherId 优惠券ID
     * @return 订单ID
     */
    @Override
    public Long seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUserId();
        long orderId = redisIdWorker.nextId("order");

        // 1. 执行 lua 脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        // 2. 判断结果为0
        if (result == null || result.intValue() != 0) {
            throw new BusinessException(result != null && result.intValue() == 1 ? ErrorCode.VOUCHER_STOCK_EMPTY : ErrorCode.VOUCHER_ALREADY_PURCHASED,
                    result != null && result.intValue() == 1 ? "库存不足" : "不能重复下单");
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

        return orderId;
    }

    /**
     * 普通优惠券
     *
     * @return 订单ID
     */
    @Override
    @Transactional
    public Long commonVoucher(Long voucherId) {
        Long userId = UserHolder.getUserId();
        String lockKey = "lock:order:" + userId + ":" + voucherId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLocked = false;

        try {
            isLocked = lock.tryLock(10, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new BusinessException(ErrorCode.SYSTEM_BUSY, "系统繁忙，请稍后再试");
            }
            Voucher voucher = voucherService.getById(voucherId);
            if (voucher == null || voucher.getStatus() != 1) {
                throw new BusinessException(ErrorCode.VOUCHER_NOT_FOUND, "优惠券不存在或已下架！");
            }
            Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                throw new BusinessException(ErrorCode.VOUCHER_ALREADY_PURCHASED, "您已经购买过该券，不可重复购买！");
            }

            // 扣减库存：普通券若关联了 tb_seckill_voucher（有库存限制）则扣减，否则不限制
            SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
            if (seckillVoucher != null && seckillVoucher.getStock() != null) {
                boolean success = seckillVoucherService.update()
                        .setSql("stock = stock - 1")
                        .eq("voucher_id", voucherId)
                        .gt("stock", 0)
                        .update();
                if (!success) {
                    throw new BusinessException(ErrorCode.VOUCHER_STOCK_EMPTY, "优惠券库存不足！");
                }
            }

            VoucherOrder voucherOrder = new VoucherOrder();
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            this.save(voucherOrder);

            return orderId;
        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断", e);
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_BUSY, "系统繁忙，请稍后再试");
        } finally {
            if (isLocked) {
                lock.unlock();
            }
        }
    }

    /**
     * 查询我的订单列表
     * @return 订单列表
     */
    @Override
    public List<VoucherOrderVO> queryMyOrders() {
        Long userId = UserHolder.getUserId();

        // 查询当前用户的所有订单
        List<VoucherOrder> orders = query().eq("user_id", userId).orderByDesc("create_time").list();
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询优惠券信息，避免 N+1
        List<Long> voucherIds = orders.stream()
                .map(VoucherOrder::getVoucherId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Voucher> voucherMap = voucherService.listByIds(voucherIds)
                .stream()
                .collect(Collectors.toMap(Voucher::getId, v -> v));

        // 转换为VO对象并填充优惠券信息
        return orders.stream().map(order -> {
            VoucherOrderVO vo = new VoucherOrderVO();
            vo.setId(order.getId());
            vo.setUserId(order.getUserId());
            vo.setVoucherId(order.getVoucherId());
            vo.setPayType(order.getPayType());
            vo.setStatus(order.getStatus());
            vo.setCreateTime(order.getCreateTime());
            vo.setPayTime(order.getPayTime());
            vo.setUseTime(order.getUseTime());
            vo.setRefundTime(order.getRefundTime());
            vo.setUpdateTime(order.getUpdateTime());

            // 从Map获取优惠券信息
            Voucher voucher = voucherMap.get(order.getVoucherId());
            if (voucher != null) {
                vo.setVoucherTitle(voucher.getTitle());
                vo.setVoucherSubTitle(voucher.getSubTitle());
                vo.setPayValue(voucher.getPayValue());
                vo.setActualValue(voucher.getActualValue());
            }

            return vo;
        }).collect(Collectors.toList());
    }
}
