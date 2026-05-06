package com.kuaiyukuaikuai.kuaiyutravel.test;

import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * 测试类：POI数据按分类同步到Redis GEO
 */
@Slf4j
@SpringBootTest
public class PoiGeoSyncTest {

    @Autowired
    private PoiService poiService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String GEO_KEY_PREFIX = "poi:geo:";

    @Test
    public void syncPoiToGeo() {
        log.info("开始同步POI数据到Redis GEO...");

        // 第一步：查询所有地点数据
        List<Poi> poiList = poiService.list();
        log.info("共查询到 {} 条POI数据", poiList.size());

        // 第二步：过滤异常数据并按typeId分组
        Map<Long, List<Poi>> poiMapByType = poiList.stream()
                .filter(poi -> poi.getX() != null && poi.getY() != null)
                .collect(Collectors.groupingBy(Poi::getTypeId));

        log.info("过滤后的数据按类型分组，共 {} 个类型", poiMapByType.size());

        // 第三步：遍历分组并同步到Redis GEO
        for (Map.Entry<Long, List<Poi>> entry : poiMapByType.entrySet()) {
            Long typeId = entry.getKey();
            List<Poi> pois = entry.getValue();

            // 拼接Redis GEO Key
            String key = GEO_KEY_PREFIX + typeId;

            // 创建GeoLocation集合
            List<RedisGeoCommands.GeoLocation<String>> locations = pois.stream()
                    .map(poi -> {
                        Point point = new Point(poi.getX(), poi.getY());
                        return new RedisGeoCommands.GeoLocation<>(poi.getId().toString(), point);
                    })
                    .collect(Collectors.toList());

            // 批量插入到Redis GEO
            if (!CollectionUtils.isEmpty(locations)) {
                stringRedisTemplate.opsForGeo().add(key, locations);
                log.info("成功将 {} 条类型为 {} 的数据同步到Redis", locations.size(), typeId);
            }
        }

        log.info("POI数据同步到Redis GEO完成！");
    }
}