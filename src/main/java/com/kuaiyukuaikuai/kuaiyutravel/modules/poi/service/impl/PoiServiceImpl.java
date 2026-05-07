package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.PoiMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiService;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.CacheClient;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.kuaiyukuaikuai.kuaiyutravel.common.utils.RedisConstants.*;

/**
 * 景点服务实现类
 */
@Slf4j
@Service
public class PoiServiceImpl extends ServiceImpl<PoiMapper, Poi> implements PoiService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 根据ID查询景点
     * @param id 景点ID
     * @return 景点详情
     */
    @Override
    public Result queryPoiById(Long id) {
        // 解决缓存穿透
        //        Poi poi = cacheClient
        //                .queryWithPassThrough(CACHE_POI_KEY, id, Poi.class, this::getById, CACHE_POI_TTL, TimeUnit.MINUTES);
        // 逻辑过期解决缓存击穿
        //         Poi poi = cacheClient
        //                .queryWithLogicalExpire(CACHE_POI_KEY, id, Poi.class, this::getById, 20L, TimeUnit.SECONDS);

        // ================== 1. 布隆过滤器前置拦截 ==================
        // 获取布隆过滤器
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter("poi:bloom-filter");

        // 判断 ID 是否存在于过滤器中
        if (!bloomFilter.contains(id)) {
            // 核心防御：布隆过滤器说没有，那就绝对没有！
            // 直接返回，绝不允许这个伪造的请求去查 Redis，更不准去查 MySQL
            log.warn("触发布隆过滤器拦截，恶意查询不存在的景点 ID: {}", id);
            return Result.fail("您查询的景点不存在！");
        }

        // ================== 2. 原有的缓存与数据库逻辑 ==================
        // 走到这里，说明布隆过滤器认为该 ID "可能存在"（允许 3% 的误判率）。
        // 接下来走你原来封装好的逻辑：先查 Redis，如果没有再尝试获取互斥锁去查 MySQL。

        // 互斥锁解决缓存击穿
        Poi poi = cacheClient
                .queryWithMutex(CACHE_POI_KEY, id, Poi.class, this::getById, CACHE_POI_TTL, TimeUnit.MINUTES);

        // 应对布隆过滤器的极小概率“误判”：如果真的误判放过来了，最后查出来还是 null，这里做最终兜底
        if (poi == null) {
            return Result.fail("地点不存在！");
        }

        // 3. 正常返回
        return Result.ok(poi);


    }

    /**
     * 更新景点信息
     * @param poi 景点信息
     * @return 操作结果
     */
    @Override
    @Transactional
    public Result update(Poi poi) {
        Long id = poi.getId();
        if (id == null) {
            return Result.fail("地点id不能为空");
        }
        // 1.更新数据库
        updateById(poi);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_POI_KEY + id);
        return Result.ok();
    }

    /**
     * 根据类型查询景点
     * @param typeId 类型ID
     * @param current 当前页码
     * @param x 经度
     * @param y 纬度
     * @return 景点列表
     */
    @Override
    public Result queryPoiByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            // 根据类型分页查询，添加排序规则
            Page<Poi> page = query()
                    .eq("type_id", typeId)
                    .orderByDesc("score")
                    .orderByDesc("id")
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 3.查询redis,按照距离排序,分页,结果:poiId, distance
        String key = POI_GEO_KEY + typeId;//typeId是店铺类型
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),// 坐标
                new Distance(500, RedisGeoCommands.DistanceUnit.KILOMETERS),// 距离，单位：公里
                //includeDistance()返回距离
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 截取from 到 end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 获取地点id
            String poiIdStr = result.getContent().getName();
            ids.add(Long.valueOf(poiIdStr));// 收集地点 ID 准备去 MySQL 查详情
            // 获取距离
            Distance distance = result.getDistance();
            distanceMap.put(poiIdStr, distance);// 用 Map 记住每个地点对应的距离
        });

        // 5.根据id查询poi
        String idStr = StrUtil.join(",", ids);
        List<Poi> pois = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Poi poi : pois) {
            poi.setDistance(distanceMap.get(poi.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(pois);
    }

    /**
     * 使用布隆过滤器保存景点
     * @param poi 景点信息
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void savePoiWithBloomFilter(Poi poi) {
        // 1. 先写入数据库
        // 注意：这里用 this.save()，如果出错会自动抛出异常，不再执行后续代码
        this.save(poi);

        // 2. 数据库写入成功，拿到自增的 ID，写入布隆过滤器
        try {
            redissonClient.getBloomFilter(POI_BLOOM_FILTER).add(poi.getId());
            log.info("POI 添加成功，已同步至布隆过滤器，ID: {}", poi.getId());
        } catch (Exception e) {
            // 如果 Redis 添加失败，抛出运行时异常，强制触发上面的 @Transactional 回滚数据库
            log.error("同步布隆过滤器失败，数据库操作将回滚，POI ID: {}", poi.getId(), e);
            throw new RuntimeException("系统繁忙，保存失败");
        }

        // 3. 追加 Redis GEO 存储逻辑
        try {
            if (poi.getX() != null && poi.getY() != null) {
                String geoKey = "poi:geo:" + poi.getTypeId();
                stringRedisTemplate.opsForGeo().add(geoKey, new org.springframework.data.geo.Point(poi.getX(), poi.getY()), poi.getId().toString());
                log.info("POI 经纬度已同步至 Redis GEO，ID: {}, 类型: {}", poi.getId(), poi.getTypeId());
            }
        } catch (Exception e) {
            // 如果 GEO 存储失败，抛出运行时异常，强制触发回滚
            log.error("同步 Redis GEO 失败，数据库操作将回滚，POI ID: {}", poi.getId(), e);
            throw new RuntimeException("系统繁忙，保存失败");
        }
    }

    /**
     * 批量保存景点并同步到布隆过滤器
     * @param poiList 景点列表
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void savePoiBatchWithBloomFilter(List<Poi> poiList) {
        // 1. 先批量写入数据库
        if (poiList == null || poiList.isEmpty()) {
            log.warn("批量保存景点列表为空");
            return;
        }

        this.saveBatch(poiList);
        log.info("批量保存 POI 成功，共 {} 条记录", poiList.size());

        // 2. 数据库写入成功，拿到自增的 ID，批量同步到布隆过滤器
        try {
            RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(POI_BLOOM_FILTER);
            
            // 按类型分组处理 Redis GEO
            Map<Long, List<Poi>> poiMapByType = poiList.stream()
                    .filter(poi -> poi.getX() != null && poi.getY() != null)
                    .collect(java.util.stream.Collectors.groupingBy(Poi::getTypeId));
            
            // 遍历分组后的 Map，批量写入 Redis GEO
            for (Map.Entry<Long, List<Poi>> entry : poiMapByType.entrySet()) {
                Long typeId = entry.getKey();
                List<Poi> pois = entry.getValue();
                
                // 拼接 Redis GEO Key
                String geoKey = POI_GEO_KEY + typeId;
                
                // 创建 GeoLocation 集合
                List<org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation<String>> locations = pois.stream()
                        .map(poi -> {
                            org.springframework.data.geo.Point point = new org.springframework.data.geo.Point(poi.getX(), poi.getY());
                            return new org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation<>(poi.getId().toString(), point);
                        })
                        .collect(java.util.stream.Collectors.toList());
                
                // 批量插入到 Redis GEO
                if (!locations.isEmpty()) {
                    stringRedisTemplate.opsForGeo().add(geoKey, locations);
                    log.info("成功将 {} 条类型为 {} 的数据同步到 Redis GEO", locations.size(), typeId);
                }
            }
            
            // 同步到布隆过滤器
            for (Poi poi : poiList) {
                bloomFilter.add(poi.getId());
            }
            
            log.info("批量 POI 已同步至布隆过滤器和 Redis GEO，共 {} 条记录", poiList.size());
        } catch (Exception e) {
            // 如果 Redis 操作失败，抛出运行时异常，强制触发回滚
            log.error("同步布隆过滤器或 Redis GEO 失败，数据库操作将回滚", e);
            throw new RuntimeException("系统繁忙，批量保存失败");
        }
    }
}