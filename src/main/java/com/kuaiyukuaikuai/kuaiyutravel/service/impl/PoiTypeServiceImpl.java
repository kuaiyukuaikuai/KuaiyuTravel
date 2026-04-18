package com.kuaiyukuaikuai.kuaiyutravel.service.impl;

import cn.hutool.json.JSONUtil;
import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.entity.PoiType;
import com.kuaiyukuaikuai.kuaiyutravel.mapper.PoiTypeMapper;
import com.kuaiyukuaikuai.kuaiyutravel.service.PoiTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 0
 * @since 2026-04-17
 */
@Service
public class PoiTypeServiceImpl extends ServiceImpl<PoiTypeMapper, PoiType> implements PoiTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        // 1. 从 Redis 查询地点类型列表
        String typeJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (typeJson != null) {
            // 3. 存在，直接反序列化返回
            List<PoiType> typeList = JSONUtil.toList(typeJson, PoiType.class);
            return Result.ok(typeList);
        }

        // 4. 不存在，查询数据库
        List<PoiType> typeList = query().orderByAsc("sort").list();
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("地点类型列表不存在");
        }

        // 5. 存在，写入 Redis 并设置过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList), RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}
