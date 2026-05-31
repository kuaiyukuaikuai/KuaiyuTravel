package com.kuaiyukuaikuai.kuaiyutravel.common.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.kuaiyukuaikuai.kuaiyutravel.common.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            r -> {
                Thread t = new Thread(r, "cache-rebuild-" + r.hashCode());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PreDestroy
    public void destroy() {
        log.info("正在关闭缓存重建线程池...");
        CACHE_REBUILD_EXECUTOR.shutdown();
        try {
            if (!CACHE_REBUILD_EXECUTOR.awaitTermination(60, TimeUnit.SECONDS)) {
                CACHE_REBUILD_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            CACHE_REBUILD_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // 将对象写入redis,物理过期 (TTL)
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 逻辑过期解决缓存击穿,将封装了实体数据和过期时间的包装类（RedisData）序列化封装成JSON字符串
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 解决缓存穿透
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1.从redis查询地点缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值
        if (json != null) {
            // 返回一个错误信息
            return null;
        }

        // 4.不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        // 5.不存在，返回错误
        if (r == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }
        // 6.存在，写入redis
        this.set(key, r, time, unit);
        return r;
    }

    // 逻辑过期解决缓存击穿
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查询地点缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3.不存在，直接返回
            return null;
        }
        // 4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，直接返回店铺信息
            return r;
        }
        // 5.2.已过期，需要缓存重建
        // 6.缓存重建
        // 6.1.获取互斥锁
        String lockKey = "lock:" + keyPrefix + id;
        boolean isLock = tryLock(lockKey);
        // 6.2.判断是否获取锁成功
        if (isLock){
            // 6.3.成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R newR = dbFallback.apply(id);
                    // 重建缓存
                    this.setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.SERVER_ERROR, "缓存重建失败");
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.4.返回过期的地点信息
        return r;
    }

    // 互斥锁解决缓存击穿,缓存穿透
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String lockKey = "lock:" + keyPrefix + id;
        int maxRetries = 10;
        int retries = 0;

        while (retries < maxRetries) {
            // 1.从redis查询地点缓存
            String poiJson = stringRedisTemplate.opsForValue().get(key);
            // 2.判断是否存在
            if (StrUtil.isNotBlank(poiJson)) {
                // 3.存在，直接返回
                return JSONUtil.toBean(poiJson, type);
            }
            // 判断命中的是否是空值
            if (poiJson != null) { // 是空串
                return null;
            }

            // 4.实现缓存重建
            // 4.1.获取互斥锁
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                // 4.2.获取锁失败，休眠后重试（循环而非递归）
                retries++;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(ErrorCode.SERVER_ERROR, "获取锁被中断");
                }
                continue;
            }

            // 4.3.获取锁成功，查询数据库并重建缓存
            try {
                // 双重检查：获取锁后再次检查缓存（可能其他线程已重建）
                String jsonAfterLock = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(jsonAfterLock)) {
                    return JSONUtil.toBean(jsonAfterLock, type);
                }
                if (jsonAfterLock != null) {
                    return null;
                }

                R r = dbFallback.apply(id);
                if (r == null) {
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                this.set(key, r, time, unit);
                return r;
            } finally {
                unlock(lockKey);
            }
        }

        // 超过最大重试次数，兜底从数据库查询（牺牲一致性保证可用性）
        log.warn("缓存互斥锁重试{}次仍未成功，直接查询数据库: key={}", maxRetries, key);
        return dbFallback.apply(id);
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}