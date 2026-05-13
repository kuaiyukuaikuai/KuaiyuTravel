package com.kuaiyukuaikuai.kuaiyutravel.test.departure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Voucher;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.SeckillVoucherService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.VoucherService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.SeckillVoucher;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiService;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 快鱼旅行 - 结合 Spring AI 的营销商品/代金券生成器 (千店千面动态策略版)
 * 核心特性：动态生成门票/套餐、一主一从外键回写、有效期配置为1年、秒杀库存同步Redis预热
 */
@Slf4j
@SpringBootTest
public class VoucherDataGeneratorTest {

    @Autowired
    private PoiService poiService;
    @Autowired
    private VoucherService voucherService;
    @Autowired
    private SeckillVoucherService seckillVoucherService;

    // 【新增】：注入 Redis 模板用于库存预热
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    @Qualifier("generatorChatClient")
    private ChatClient chatClient;

    @Autowired
    @Qualifier("aiThreadPool")
    private ExecutorService aiThreadPool;

    private static final Random RANDOM = new Random();

    // 保持与业务层一致的 Redis Key 前缀
    private static final String SECKILL_STOCK_KEY = "seckill:stock:";

    /**
     * AI 返回的单张商品/代金券结构
     */
    @Data
    public static class AIVoucher {
        @JsonProperty("title")
        private String title;
        @JsonProperty("sub_title")
        private String subTitle;
        @JsonProperty("rules")
        private String rules;
        @JsonProperty("type")
        private Integer type; // 0普通，1秒杀
        @JsonProperty("pay_value")
        private Long payValue; // 支付金额(分)
        @JsonProperty("actual_value")
        private Long actualValue; // 原价/面值(分)
    }

    /**
     * AI 返回的外层包装
     */
    @Data
    public static class AIVoucherResponse {
        @JsonProperty("vouchers")
        private List<AIVoucher> vouchers;
    }

    @Test
    public void generateFakeVouchersAsync() {
        List<Poi> pois = poiService.list(new LambdaQueryWrapper<Poi>()
                .select(Poi::getId, Poi::getName, Poi::getTypeId, Poi::getAvgPrice));

        if (pois.isEmpty()) {
            log.error("警告：目的地 POI 表为空，无法绑定代金券！");
            return;
        }

        log.info("开始为 {} 个目的地配置差异化商品，开启多线程并发...", pois.size());

        List<CompletableFuture<List<Voucher>>> futures = new ArrayList<>();
        BeanOutputConverter<AIVoucherResponse> converter = new BeanOutputConverter<>(AIVoucherResponse.class);

        for (Poi targetPoi : pois) {
            CompletableFuture<List<Voucher>> future = CompletableFuture.supplyAsync(() -> {
                return generateVouchersForPoi(targetPoi, converter);
            }, aiThreadPool);
            futures.add(future);
        }

        log.info("任务已下发至 AI 运营总监，正在根据行业特性生成商品策略...");

        List<Voucher> allVouchers = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        if (allVouchers.isEmpty()) {
            log.warn("未能生成任何商品/代金券。");
            return;
        }

        // ================= 3. 架构核心：两段式级联落库 =================
        voucherService.saveBatch(allVouchers);
        log.info("成功向 MySQL 压入 {} 张基础商品/代金券记录！", allVouchers.size());

        List<SeckillVoucher> seckillVouchers = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Voucher v : allVouchers) {
            if (v.getType() != null && v.getType() == 1) { // 1 代表秒杀券
                SeckillVoucher sv = new SeckillVoucher();
                sv.setVoucherId(v.getId());
                sv.setStock(RANDOM.nextInt(100) + 20); // 随机库存 20~119

                // 【核心修改】：即刻生效，失效时间统一设置为 1 年以后
                sv.setBeginTime(now);
                sv.setEndTime(now.plusYears(1));

                seckillVouchers.add(sv);
            }
        }

        if (!seckillVouchers.isEmpty()) {
            // 将秒杀券属性写入 MySQL
            seckillVoucherService.saveBatch(seckillVouchers);
            log.info("成功向 MySQL 压入 {} 条秒杀库存与时间配置明细！", seckillVouchers.size());

            // ================= 4. 秒杀库存预热到 Redis =================
            for (SeckillVoucher sv : seckillVouchers) {
                // key 格式: seckill:stock:{voucherId}
                String redisKey = SECKILL_STOCK_KEY + sv.getVoucherId();
                stringRedisTemplate.opsForValue().set(redisKey, sv.getStock().toString());
            }
            log.info("🚀 成功将 {} 条秒杀券库存同步预热至 Redis 中！", seckillVouchers.size());
        }

        log.info("🎉 营销商品模块数据生成圆满完成！");
    }

    /**
     * 子线程执行：为一个地点生成商品（动态策略）
     */
    private List<Voucher> generateVouchersForPoi(Poi poi, BeanOutputConverter<AIVoucherResponse> converter) {
        List<Voucher> result = new ArrayList<>();

        String typeName = getPoiTypeStr(poi.getTypeId());
        String productStrategy = getProductStrategy(poi.getTypeId());
        long basePrice = (poi.getAvgPrice() != null && poi.getAvgPrice() > 0) ? poi.getAvgPrice() : 100L;

        // 动态随机数量：普通商品(1~3个)，秒杀商品(1~2个)
        int normalCount = RANDOM.nextInt(3) + 1;
        int seckillCount = RANDOM.nextInt(2) + 1;
        int totalCount = normalCount + seckillCount;

        try {
            String promptString = """
                    你是“快鱼旅行”的高级运营专家。请为目的地【{poiName}】(类型:{poiType}，人均消费约{basePrice}元) 设计 {totalCount} 款售卖商品。
                    
                    该地点的商品形态应严格遵循以下策略：【{productStrategy}】。

                    具体要求：
                    1. 必须包含 {normalCount} 款【普通商品】(type设为0)，如标准门票、大床房、普通套餐等。
                    2. 必须包含 {seckillCount} 款【限量秒杀商品】(type设为1)，折扣力度极大，限时抢购。
                    3. title必须高度贴合地点类型（如“双人特惠套餐”、“豪华观景双床房一晚”、“成人通票”等）。
                    4. sub_title写明包含内容或门槛，rules写一句简短规则(如需提前一天预约、周末节假日可用等)。
                    5. pay_value(实际支付价)和actual_value(原价/面值)的单位是【分】！例如 88元 必须输出数字 8800。
                    {format}
                    """;

            PromptTemplate template = new PromptTemplate(promptString);
            template.add("poiName", poi.getName());
            template.add("poiType", typeName);
            template.add("basePrice", basePrice);
            template.add("productStrategy", productStrategy);
            template.add("totalCount", totalCount);
            template.add("normalCount", normalCount);
            template.add("seckillCount", seckillCount);
            template.add("format", converter.getFormat());

            String responseText = chatClient.prompt(template.create()).call().content();

            // 强制清洗 JSON
            if (responseText != null) {
                int start = responseText.indexOf("{");
                int end = responseText.lastIndexOf("}");
                if (start != -1 && end != -1 && start <= end) {
                    responseText = responseText.substring(start, end + 1);
                }
            }

            AIVoucherResponse aiResponse = null;
            try {
                if (StringUtils.hasText(responseText)) {
                    aiResponse = converter.convert(responseText);
                }
            } catch (Exception e) {
                log.warn("AI返回格式解析失败，启动兜底...");
            }

            // 组装返回结果
            if (aiResponse != null && aiResponse.getVouchers() != null && !aiResponse.getVouchers().isEmpty()) {
                for (AIVoucher av : aiResponse.getVouchers()) {
                    Voucher v = new Voucher();
                    v.setPoiId(poi.getId());
                    v.setTitle(av.getTitle());
                    v.setSubTitle(av.getSubTitle());
                    v.setRules(av.getRules());
                    v.setType(av.getType() != null ? av.getType() : 0);
                    v.setPayValue(av.getPayValue() != null ? av.getPayValue() : (basePrice * 80)); // 默认打八折
                    v.setActualValue(av.getActualValue() != null ? av.getActualValue() : (basePrice * 100));
                    v.setStatus(1);
                    result.add(v);
                }
            } else {
                throw new RuntimeException("AI生成的商品数量为空");
            }

            Thread.sleep(150);

        } catch (Exception e) {
            log.warn("为地点【{}】生成商品失败: {}。触发架构师代码级兜底！", poi.getName(), e.getMessage());
            result = buildFallbackVouchers(poi, normalCount, seckillCount, typeName);
        }

        return result;
    }

    /**
     * 架构师级兜底：动态生成补位数据
     */
    private List<Voucher> buildFallbackVouchers(Poi poi, int normalCount, int seckillCount, String typeName) {
        List<Voucher> fallbacks = new ArrayList<>();
        long basePay = 8800L;
        long baseActual = 10000L;

        // 补足普通商品
        for (int i = 0; i < normalCount; i++) {
            Voucher v = new Voucher();
            v.setPoiId(poi.getId());
            v.setTitle(typeName + " - 优选套餐/门票/客房");
            v.setSubTitle("全场通用，免预约");
            v.setRules("周末节假日通用");
            v.setPayValue(basePay);
            v.setActualValue(baseActual);
            v.setType(0);
            v.setStatus(1);
            fallbacks.add(v);
        }

        // 补足秒杀商品
        for (int i = 0; i < seckillCount; i++) {
            Voucher v = new Voucher();
            v.setPoiId(poi.getId());
            v.setTitle(typeName + " - 限量秒杀特惠");
            v.setSubTitle("限量秒杀，售完即止");
            v.setRules("需提前预约，不可退换");
            v.setPayValue(basePay / 2); // 秒杀价半价
            v.setActualValue(baseActual);
            v.setType(1);
            v.setStatus(1);
            fallbacks.add(v);
        }

        return fallbacks;
    }

    private String getPoiTypeStr(Long typeId) {
        if (typeId == null) return "热门打卡地";
        return switch (typeId.intValue()) {
            case 1 -> "特色美食";
            case 2 -> "热门景点";
            case 3 -> "精选酒店";
            case 4 -> "休闲娱乐";
            default -> "宝藏目的地";
        };
    }

    /**
     * 获取对应 POI 类型的商品售卖策略，用于引导大模型生成精准数据
     */
    private String getProductStrategy(Long typeId) {
        if (typeId == null) return "通用代金券或体验套餐";
        return switch (typeId.intValue()) {
            case 1 -> "主打【美食团购套餐】（如招牌双人餐、豪华四人餐）和【通用代金券】";
            case 2 -> "主打【门票】（如成人门票、学生票、亲子套票、VIP免排队通道票）";
            case 3 -> "主打【酒店客房预订】（如高级大床房一晚、双床房连住特惠、豪华套房）";
            case 4 -> "主打【休闲娱乐项目】（如单人体验券、欢唱套餐、按摩足疗套餐、VIP畅玩票）";
            default -> "通用代金券或体验套餐";
        };
    }
}