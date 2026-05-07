package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.impl;

import cn.hutool.json.JSONUtil;
import com.kuaiyukuaikuai.kuaiyutravel.modules.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.PoiType;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.PoiTypeMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 景点类型服务实现类
 */
@Service
public class PoiTypeServiceImpl extends ServiceImpl<PoiTypeMapper, PoiType> implements PoiTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询类型列表
     * @return 类型列表
     */
    @Override
    public Result queryTypeList() {
        String key = RedisConstants.CACHE_POI_TYPE_KEY;
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
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList), RedisConstants.CACHE_POI_TYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}