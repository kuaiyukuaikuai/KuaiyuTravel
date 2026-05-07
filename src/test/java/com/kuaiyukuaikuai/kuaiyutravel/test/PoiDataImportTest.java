package com.kuaiyukuaikuai.kuaiyutravel.test;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiService;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * POI数据导入测试类
 * 用于调用高德地图API获取景点数据并批量导入
 */
@Slf4j
@SpringBootTest
public class PoiDataImportTest {

    // 高德地图API密钥，需要替换为真实的API Key
    private static final String API_KEY = "a311f1bc9631436c35ef8f8d82d1219f";

    // 默认图片URL
    private static final String DEFAULT_IMAGE_URL = "E:\\Code\\project\\KuaiyuTravel\\KuaiyuTravel\\src\\main\\resources\\images_test.png";

    @Resource
    private PoiService poiService;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private AliOssUtil aliOssUtil;

    /**
     * 从高德地图API获取指定城市和类型的地点数据并批量导入
     * @param city 城市名称
     * @param keyword 搜索关键字
     * @param typeId 地点类型ID
     */
    private void doImport(String city, String keyword, Long typeId) {
        log.info("正在抓取 [{}] 市的 [{}] 数据...", city, keyword);

        // 构建请求URL
        String url = "https://restapi.amap.com/v3/place/text";

        // 构建请求参数
        String params = "?key=" + API_KEY + "&keywords=" + keyword + "&city=" + city + "&extensions=all&offset=50&page=1";

        // 发送HTTP请求
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url + params, HttpMethod.GET, entity, String.class);

        // 解析响应数据
        String responseBody = response.getBody();
        JSONObject jsonObject = JSONUtil.parseObj(responseBody);

        // 检查请求是否成功
        String status = jsonObject.getStr("status");
        if (!"1".equals(status)) {
            log.error("高德地图API请求失败: {}", jsonObject.getStr("info"));
            return;
        }

        // 解析地点数据
        JSONArray pois = jsonObject.getJSONArray("pois");
        List<Poi> poiList = new ArrayList<>();

        for (int i = 0; i < pois.size(); i++) {
            JSONObject poiJson = pois.getJSONObject(i);
            Poi poi = new Poi();

            // 填充必填字段
            poi.setName(poiJson.getStr("name"));
            poi.setTypeId(typeId); // 使用传入的类型ID
            poi.setAddress(poiJson.getStr("address"));
            
            // 解析经纬度
            String location = poiJson.getStr("location");
            if (location != null && !location.isEmpty()) {
                String[] coords = location.split(",");
                if (coords.length == 2) {
                    poi.setX(Double.parseDouble(coords[0]));
                    poi.setY(Double.parseDouble(coords[1]));
                }
            }

            // 处理图片
            String imageUrl = processImage(poiJson);
            poi.setImages(imageUrl);

            // 填充可选字段
            poi.setArea(poiJson.getStr("business_area"));
            poi.setAvgPrice(100L); // 默认均价
            poi.setSold(0); // 默认销量
            poi.setComments(0); // 默认评论数
            poi.setScore(45); // 默认评分（4.5分）
            poi.setOpenHours("09:00-18:00"); // 默认营业时间
            poi.setCreateTime(new Date());
            poi.setUpdateTime(new Date());

            poiList.add(poi);
        }

        log.info("解析完成，共获取到 {} 个 [{}] 数据", poiList.size(), keyword);

        // 批量导入数据
        if (!poiList.isEmpty()) {
            log.info("开始批量导入 [{}] 数据...", keyword);
            try {
                poiService.savePoiBatchWithBloomFilter(poiList);
                log.info("批量导入成功，共导入 {} 个 [{}]", poiList.size(), keyword);
            } catch (Exception e) {
                log.error("批量导入失败", e);
            }
        }
    }

    /**
     * 处理图片：从高德API获取图片并转存到阿里云OSS
     * @param poiJson 高德API返回的地点JSON对象
     * @return 图片URL
     */
    private String processImage(JSONObject poiJson) {
        try {
            // 尝试提取高德的photos数组
            JSONArray photos = poiJson.getJSONArray("photos");
            if (photos != null && photos.size() > 0) {
                JSONObject firstPhoto = photos.getJSONObject(0);
                String imgUrl = firstPhoto.getStr("url");
                if (imgUrl != null && !imgUrl.isEmpty()) {
                    // 下载图片
                    byte[] imageBytes = restTemplate.getForObject(imgUrl, byte[].class);
                    if (imageBytes != null && imageBytes.length > 0) {
                        // 包装成MultipartFile
                        String fileName = UUID.randomUUID().toString() + ".jpg";
                        MockMultipartFile mockFile = new MockMultipartFile(
                                "file",
                                fileName,
                                "image/jpeg",
                                imageBytes
                        );
                        // 上传到OSS
                        String ossUrl = aliOssUtil.upload(mockFile, "pois");
                        log.info("图片上传成功，OSS URL: {}", ossUrl);
                        return ossUrl;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("图片处理失败，使用默认图片", e);
        }
        // 降级使用默认图片
        return DEFAULT_IMAGE_URL;
    }

    /**
     * 导入所有城市和类型的POI数据
     */
    @Test
    public void importAllPoiData() {
        log.info("开始导入所有POI数据...");

        // 定义要抓取的城市列表
        List<String> cities = Arrays.asList("梅州", "揭阳", "潮州");

        // 定义地点类型与关键字的映射关系
        List<TypeKeywordMapping> mappings = Arrays.asList(
                new TypeKeywordMapping(1L, "美食"),
                new TypeKeywordMapping(2L, "景点"),
                new TypeKeywordMapping(3L, "酒店"),
                new TypeKeywordMapping(4L, "娱乐")
        );

        // 双重循环，遍历城市和类型
        for (String city : cities) {
            for (TypeKeywordMapping mapping : mappings) {
                doImport(city, mapping.getKeyword(), mapping.getTypeId());
                
                // 高德API有QPS限制，暂停1秒
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.error("线程睡眠异常", e);
                    Thread.currentThread().interrupt();
                }
            }
        }

        log.info("所有POI数据导入完成！");
    }

    /**
     * 类型与关键字映射类
     */
    private static class TypeKeywordMapping {
        private final Long typeId;
        private final String keyword;

        public TypeKeywordMapping(Long typeId, String keyword) {
            this.typeId = typeId;
            this.keyword = keyword;
        }

        public Long getTypeId() {
            return typeId;
        }

        public String getKeyword() {
            return keyword;
        }
    }
}