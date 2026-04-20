package com.kuaiyukuaikuai.kuaiyutravel.service.impl;

import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.service.PoiService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;
import java.util.List;

@SpringBootTest
public class PoiServiceTest {

    @Resource
    private PoiService poiService;

    @Test
    public void testQueryPoiByType() {
        // 广东省梅州市梅江区嘉应学院江北校区的经纬度
        // 经度：116.124
        // 纬度：24.294
        double x = 116.124;
        double y = 24.294;
        
        // 测试类型 ID 为 1 的情况
        Integer typeId = 1;
        Integer current = 1;
        
        // 调用 queryPoiByType 方法
        Result result = poiService.queryPoiByType(typeId, current, x, y);
        
        // 解析返回结果
        List<Poi> pois = (List<Poi>) result.getData();
        
        // 打印结果数量
        System.out.println("查询到的地点数量：" + pois.size());
        
        // 打印每个地点的名称和距离
        for (Poi poi : pois) {
            System.out.println("地点名称：" + poi.getName() + "，距离：" + poi.getDistance() + " 公里");
        }
    }
}