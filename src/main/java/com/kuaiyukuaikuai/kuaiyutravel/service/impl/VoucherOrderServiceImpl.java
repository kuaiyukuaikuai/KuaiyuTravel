package com.kuaiyukuaikuai.kuaiyutravel.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.entity.Voucher;
import com.kuaiyukuaikuai.kuaiyutravel.entity.VoucherOrder;
import com.kuaiyukuaikuai.kuaiyutravel.service.SeckillVoucherService;
import com.kuaiyukuaikuai.kuaiyutravel.service.VoucherOrderService;
import com.kuaiyukuaikuai.kuaiyutravel.mapper.VoucherOrderMapper;
import com.kuaiyukuaikuai.kuaiyutravel.service.VoucherService;
import com.kuaiyukuaikuai.kuaiyutravel.utils.RedisIdWorker;
import com.kuaiyukuaikuai.kuaiyutravel.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
* @author 0
* @description 针对表【tb_voucher_order】的数据库操作Service实现
* @createDate 2026-04-17 11:08:15
*/
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
    implements VoucherOrderService{

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
    @Resource
// 必须加 @Lazy，否则 Spring 初始化时会因为循环依赖报错
    @Lazy
    private VoucherOrderService proxy;

    /*@Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
        //创建锁对象(新增代码)
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁对象
        boolean isLock = lock.tryLock();
        //加锁失败
        if (!isLock) {
            return Result.fail("不允许重复下单");
        }
        try {
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }
    }*/

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    
    // 用于控制消费者线程的停止
    private volatile boolean running = true;
    
    /**
     * 监听Spring容器关闭事件，尽早停止消费者线程
     * Ordered.HIGHEST_PRECEDENCE 确保最先执行
     */
    @EventListener(ContextClosedEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onContextClosed() {
        log.info("检测到容器关闭信号，立即停止消费者线程");
        running = false;
    }

    @PostConstruct
    public void init() {
// 1. 初始化 Redis Stream 和 消费者组
        try {
            stringRedisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.execute("XGROUP",
                        "CREATE".getBytes(),
                        "stream.orders".getBytes(),
                        "g1".getBytes(),
                        "0".getBytes(),
                        "MKSTREAM".getBytes());
                return null;
            });
            log.info("✅ 自动创建 Stream 队列和消费者组 g1 成功！");
        } catch (Exception e) {
            // 检查是否是消费者组已存在的异常
            String errorMsg = e.getMessage();
            Throwable cause = e.getCause();
            boolean isBusyGroup = (errorMsg != null && errorMsg.contains("BUSYGROUP")) ||
                                  (cause != null && cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP"));
            
            if (isBusyGroup) {
                log.info("ℹ️ 消费者组 g1 已经存在，无需重复创建。");
            } else {
                log.error("❌ 初始化消息队列失败：", e);
            }
        }

        // 2. 基建准备完毕，再启动消费者线程
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    
    /**
     * 应用关闭时停止消费者线程
     */
    @PreDestroy
    public void destroy() {
        log.info("正在关闭秒杀订单消费者线程...");
        running = false;
        SECKILL_ORDER_EXECUTOR.shutdown();
        try {
            if (!SECKILL_ORDER_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                SECKILL_ORDER_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            SECKILL_ORDER_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("秒杀订单消费者线程已关闭");
    }

    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";

        @Override
        public void run() {

            while (running) {
                try {
                    // 1.获取消息队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2.判断信息获取是否成功
                    // 2.1 获取失败,说明没有消息,继续循环
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    // 3.解析消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 2.2 获取成功 创建订单
                    handleVoucherOrder(voucherOrder);
                    // 4.ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    // 应用关闭时的异常不需要记录
                    if (running) {
                        log.error("处理订单异常", e);
                        // 只有在运行时才处理pending消息，避免关闭时访问Redis
                        try {
                            handlePendingList();
                        } catch (Exception ex) {
                            // 关闭时的异常忽略
                            if (running) {
                                log.error("处理pending订单异常", ex);
                            }
                        }
                    }
                }
            }
        }

        private void handlePendingList() {
            while (running) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有异常消息，结束循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    handleVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    // 应用关闭时直接退出，不再记录异常
                    if (!running) {
                        break;
                    }
                    log.error("处理pending订单异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ee) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        /*       private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }*/
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 获取用户
        Long userId = voucherOrder.getUserId();
        // 创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败，返回错误或重试
            log.error("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }



    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 获取订单id
        long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        // 2.判断结果为0
        if (result.intValue() != 0) {
            return Result.fail(result.intValue() == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.1 不为0,没有下单资格
        // 2.2 为0,下单,把下单信息保存到阻塞队列


        // 3.返回订单id
        return Result.ok(orderId);
    }


/*    @Override
    public Result commonVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 获取订单id
        long orderId = redisIdWorker.nextId("order");

        // 创建订单对象
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        // 直接创建订单
        createVoucherOrder(voucherOrder);

        // 返回订单id
        return Result.ok(orderId);
    }*/




    /**
     * 创建订单
     *
     * @param voucherOrder 订单信息
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 5.1.用户id
        Long userId = voucherOrder.getUserId();
        long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            log.error("用户已经购买过一次！");
            return;
        }

        //5，扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock= stock -1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if (!success) {
            //扣减库存
            log.error("库存不足！");
            return;
        }
        save(voucherOrder);
    }



    @Override
    @Transactional
    public Result commonVoucher(Long voucherId) {
        // 1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        // 2. 生成分布式锁 key
        String lockKey = "lock:order:" + userId + ":" + voucherId;

        // 3. 获取分布式锁
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLocked = false;

        try {
            // 尝试获取锁，设置 10 秒超时
            isLocked = lock.tryLock(10, TimeUnit.SECONDS);
            if (!isLocked) {
                // 获取锁失败，返回错误
                return Result.fail("系统繁忙，请稍后再试");
            }

            // 4. 查询优惠券信息
            Voucher voucher = voucherService.getById(voucherId);
            if (voucher == null) {
                return Result.fail("优惠券不存在！");
            }

            // 5. 检查是否已经购买过
            Long count = query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count();
            if (count > 0) {
                return Result.fail("您已经购买过该券，不可重复购买！");
            }

            // 6. 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 6.1 设置订单全局唯一 ID
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 6.2 设置用户 ID
            voucherOrder.setUserId(userId);
            // 6.3 设置代金券 ID
            voucherOrder.setVoucherId(voucherId);

            // 7. 保存订单
            this.save(voucherOrder);

            // 8. 返回订单 ID
            return Result.ok(orderId);
        } catch (InterruptedException e) {
            // 处理中断异常
            log.error("获取锁中断", e);
            return Result.fail("系统繁忙，请稍后再试");
        } finally {
            // 释放锁
            if (isLocked) {
                lock.unlock();
            }
        }
    }
}




