package com.kuaiyukuaikuai.kuaiyutravel.test.poi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.AliOssUtil;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 快鱼旅行 - 高德地图 v5 周边搜索 POI 数据抓取与同步 (高可用架构终极版)
 * 具备：多线程并发转存、多值字段正则清洗、唯一ID提取、强制距离排序防重
 */
@Slf4j
@SpringBootTest
public class PoiDataImportTest {

    @Autowired
    private PoiService poiService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AliOssUtil aliOssUtil;

    // TODO: 请替换为你自己的高德地图 Web服务 API Key
    private static final String AMAP_API_KEY = "a311f1bc9631436c35ef8f8d82d1219f";

    // 【核心架构升级 1】：URL 中新增 sortrule=distance，强制距离排序，防止分页数据漂移导致重复
    private static final String AMAP_AROUND_URL =
            "https://restapi.amap.com/v5/place/around?key={key}&location={location}&radius={radius}&types={types}&page_size={pageSize}&page_num={pageNum}&sortrule=distance&show_fields=business,photos";

    // 定义专用于图片下载与 OSS 上传的 IO 密集型线程池
    private final ExecutorService ioThreadPool = new ThreadPoolExecutor(
            10, 20, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由主线程执行，防止任务丢失
    );

    @Test
    public void importPoiDataFromAmapAroundAsync() {
        // ==================== 核心探索区域配置 ====================
        String location = "116.127059,24.331337"; // 中心点经纬度
        int radius = 50000;                       // 搜索半径，单位：米
        String types = "080300";                 // 目标POI分类码
        Long targetTypeId = 4L;                  // 对应 tb_poi_type 的类型主键ID
        int pageSize = 20;                       // 单页拉取数量
        int pagesToFetch = 5;                    // 抓取页数（深度）
        // ==========================================================

        log.info("开始拉取中心点【{}】周边目的地数据，启用多线程并发及防重机制...", location);

        List<Poi> poiList = new ArrayList<>();

        for (int pageNum = 1; pageNum <= pagesToFetch; pageNum++) {
            log.info("正在从高德拉取第 {} 页数据...", pageNum);

            try {
                // 发起周边搜索网络请求
                ResponseEntity<String> response = restTemplate.getForEntity(
                        AMAP_AROUND_URL, String.class,
                        AMAP_API_KEY, location, radius, types, pageSize, pageNum
                );

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) continue;

                JsonNode rootNode = objectMapper.readTree(response.getBody());
                if (!"1".equals(rootNode.path("status").asText())) {
                    log.error("高德API返回异常，info: {}", rootNode.path("info").asText());
                    break;
                }

                JsonNode poisNode = rootNode.path("pois");
                if (poisNode.isEmpty()) {
                    log.info("周边目的地数据已探索完毕，当前层无更多数据。");
                    break;
                }

                // 收集当前批次所有目的地处理图片的 Future 任务
                List<CompletableFuture<Void>> batchImageFutures = new ArrayList<>();

                for (JsonNode node : poisNode) {
                    Poi poi = new Poi();

                    // 【核心架构升级 2】：提取并保存高德原生地图 ID (务必确保 Poi 实体类中已添加 amapId 字段)
                    poi.setAmapId(getAmapString(node, "id"));

                    // ====== 1. 基础信息解析 ======
                    poi.setName(getAmapString(node, "name"));
                    poi.setTypeId(targetTypeId);

                    // 地址拼接：省+市+区+详细地址
                    String pname = getAmapString(node, "pname");
                    String cityname = getAmapString(node, "cityname");
                    String adname = getAmapString(node, "adname");
                    String rawAddress = getAmapString(node, "address");
                    poi.setAddress(String.format("%s%s%s%s", pname, cityname, adname, rawAddress));

                    // 经纬度拆解
                    String poiLocation = getAmapString(node, "location");
                    if (StringUtils.hasText(poiLocation) && poiLocation.contains(",")) {
                        String[] coords = poiLocation.split(",");
                        poi.setX(Double.parseDouble(coords[0]));
                        poi.setY(Double.parseDouble(coords[1]));
                    } else {
                        continue; // 缺少坐标的脏数据直接丢弃
                    }

                    // ====== 2. 扩展字段处理 (Business) ======
                    JsonNode businessNode = node.path("business");
                    poi.setArea(getAmapString(businessNode, "business_area"));

                    // 多电话支持与数据清洗
                    String rawPhone = getAmapString(businessNode, "tel");
                    if (StringUtils.hasText(rawPhone)) {
                        String formattedPhone = rawPhone.replaceAll("[;\\s]+", ",");
                        if (formattedPhone.length() > 128) {
                            formattedPhone = formattedPhone.substring(0, 128);
                        }
                        poi.setPhone(formattedPhone);
                    } else {
                        poi.setPhone(null);
                    }

                    // 多段营业时间支持与数据清洗
                    String rawOpenHours = getAmapString(businessNode, "opentime_today");
                    if (StringUtils.hasText(rawOpenHours)) {
                        String formattedHours = rawOpenHours.replaceAll("[;\\s]+", ",");
                        if (formattedHours.length() > 255) {
                            formattedHours = formattedHours.substring(0, 255);
                        }
                        poi.setOpenHours(formattedHours);
                    } else {
                        poi.setOpenHours(null);
                    }

                    // 人均消费 & 评分
                    String costStr = getAmapString(businessNode, "cost");
                    poi.setAvgPrice(!StringUtils.hasText(costStr) ? null : (long) Double.parseDouble(costStr));

                    String ratingStr = getAmapString(businessNode, "rating");
                    double rating = !StringUtils.hasText(ratingStr) ? 0.0 : Double.parseDouble(ratingStr);
                    poi.setScore((int) (rating * 10));

                    // 初始化业务统计数据
                    poi.setSold(0);
                    poi.setComments(0);

                    // ====== 3. 多线程并发图片转存 (Photos) ======
                    JsonNode photos = node.path("photos");
                    if (photos.isArray() && !photos.isEmpty()) {
                        List<CompletableFuture<String>> singlePoiImageFutures = new ArrayList<>();

                        for (JsonNode photoNode : photos) {
                            String amapImgUrl = photoNode.path("url").asText("");
                            if (StringUtils.hasText(amapImgUrl)) {
                                CompletableFuture<String> imgFuture = CompletableFuture.supplyAsync(() -> {
                                    return downloadAndUploadToOss(amapImgUrl);
                                }, ioThreadPool);
                                singlePoiImageFutures.add(imgFuture);
                            }
                        }

                        // 汇集当前目的地所有的图片 Future
                        CompletableFuture<Void> poiImagesTask = CompletableFuture.allOf(
                                singlePoiImageFutures.toArray(new CompletableFuture[0])
                        ).thenAccept(v -> {
                            String imagesStr = singlePoiImageFutures.stream()
                                    .map(CompletableFuture::join)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.joining(","));
                            poi.setImages(StringUtils.hasText(imagesStr) ? imagesStr : "");
                        });

                        batchImageFutures.add(poiImagesTask);
                    } else {
                        poi.setImages(""); // 无图片兜底
                    }

                    poiList.add(poi);
                }

                // 等待当前页所有目的地图片并发转存完成
                if (!batchImageFutures.isEmpty()) {
                    CompletableFuture.allOf(batchImageFutures.toArray(new CompletableFuture[0])).join();
                    log.info("第 {} 页所有目的地图片并发转存完成！", pageNum);
                }

                // 严格 API 限流保护
                Thread.sleep(500);

            } catch (Exception e) {
                log.error("探索第 {} 页数据发生异常", pageNum, e);
            }
        }

        // ====== 4. 批量数据持久化 (兼容数据库唯一索引) ======
        if (!poiList.isEmpty()) {
            log.info("探索结束，准备将 {} 个目的地纳入旅行图鉴...", poiList.size());
            try {
                // 如果你的 savePoiBatchWithBloomFilter 底层是普通的 saveBatch，遇到重复会抛异常
                poiService.savePoiBatchWithBloomFilter(poiList);
                log.info("旅行图鉴批量更新完成！");
            } catch (DuplicateKeyException e) {
                // 【架构容错机制】
                // 如果抛出 DuplicateKeyException，说明这批数据里有我们数据库已存在的目的地(amap_id冲突)
                // 此时建议在 service 层采用循环单条捕获插入，或使用 saveOrUpdateBatch
                log.warn("触发数据库防重保护！检测到重复的目的地数据，建议在 Service 层更换为 saveOrUpdateBatch 方法。");
            }
        }
    }

    /**
     * 独立的图片下载与 OSS 上传原子方法
     */
    private String downloadAndUploadToOss(String amapImgUrl) {
        try {
            ResponseEntity<byte[]> imgResp = restTemplate.getForEntity(amapImgUrl, byte[].class);
            if (imgResp.getStatusCode().is2xxSuccessful() && imgResp.getBody() != null) {
                String fileName = UUID.randomUUID() + ".jpg";
                MultipartFile multipartFile = new MockMultipartFile("file", fileName, "image/jpeg", imgResp.getBody());
                return aliOssUtil.upload(multipartFile, "poi-images");
            }
        } catch (Exception e) {
            log.warn("单张图片资源拉取或转存失败，自动跳过: {}", amapImgUrl);
        }
        return null;
    }

    /**
     * 健壮的 API 字段清洗工具
     */
    private String getAmapString(JsonNode node, String path) {
        if (node == null || node.isMissingNode()) return null;
        String value = node.path(path).asText("");
        return ("[]".equals(value) || !StringUtils.hasText(value)) ? null : value;
    }
}