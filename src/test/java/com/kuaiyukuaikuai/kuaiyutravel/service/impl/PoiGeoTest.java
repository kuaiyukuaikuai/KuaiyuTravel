package com.kuaiyukuaikuai.kuaiyutravel.service.impl;

import com.kuaiyukuaikuai.kuaiyutravel.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.service.PoiService;
import com.kuaiyukuaikuai.kuaiyutravel.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import jakarta.annotation.Resource;
import java.util.List;

@SpringBootTest
public class PoiGeoTest {

    @Resource
    private PoiService poiService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void importPoiGeoData() {
        // 1. 查询所有 POI 地点
        List<Poi> pois = poiService.list();
        
        // 2. 遍历 POI 地点，将每个地点的地理位置存入 Redis
        for (Poi poi : pois) {
            // 获取地点的经纬度
            Double x = poi.getX(); // 经度
            Double y = poi.getY(); // 纬度
            Long typeId = poi.getTypeId();
            Long id = poi.getId();
            
            // 构建 Redis key: poi:geo:{typeId}
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            
            // 将地点的地理位置存入 Redis
            stringRedisTemplate.opsForGeo().add(
                    key,
                    new RedisGeoCommands.GeoLocation<>(
                            id.toString(),
                            new org.springframework.data.geo.Point(x, y)
                    )
            );
            
            // 确保键是永久的，不会过期
            stringRedisTemplate.persist(key);
        }
        
        System.out.println("POI 地理数据导入完成！");
    }
}