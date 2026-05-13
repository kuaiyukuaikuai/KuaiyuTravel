package com.kuaiyukuaikuai.kuaiyutravel.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Blog;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.PoiComment;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.BlogMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.PoiCommentMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.PoiMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@SpringBootTest
public class MilvusDataImportTest {

    @Resource
    private VectorStore vectorStore;

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private PoiCommentMapper commentMapper;

    @Resource
    private PoiMapper poiMapper;

    @Test
    public void importAllDataToMilvus() {
        log.info("========== 开始全量知识库洗盘任务 (扩维增强版) ==========");

        // 🚀 架构师优化：缓存整个 Poi 对象，而不仅是名字
        List<Poi> allPois = poiMapper.selectList(null);
        Map<Long, Poi> poiMap = allPois.stream()
                .collect(Collectors.toMap(Poi::getId, poi -> poi));

        // 1. 导入游记 (Blog)
        importBlogs(poiMap);

        // 2. 导入评论 (Comment)
        importComments(poiMap);

        log.info("========== 全量知识库洗盘任务圆满完成！ ==========");
    }

    private void importBlogs(Map<Long, Poi> poiMap) {
        log.info("正在查询 MySQL 中的游记数据...");
        List<Blog> blogs = blogMapper.selectList(null);

        if (blogs == null || blogs.isEmpty()) {
            log.info("没有找到游记数据，跳过。");
            return;
        }

        List<Document> documents = new ArrayList<>();
        for (Blog blog : blogs) {
            Long poiId = blog.getPoiId() != null ? blog.getPoiId() : 0L;
            Poi poi = poiMap.get(poiId);

            // 🚀 核心逻辑：组装极其丰满的 POI 附加信息
            String poiInfo = buildPoiInfoString(poi);
            String poiName = poi != null ? poi.getName() : "未知景点";

            // 🚀 将 POI 静态信息与游记内容完美融合
            String text = String.format("这是一篇关于【%s】的快鱼旅行用户游记。该目的地详情如下：%s。游记标题：【%s】，内容：【%s】",
                    poiName, poiInfo, blog.getTitle(), blog.getContent());

            // 元数据中也存入一份核心数据，方便后续 Filter 过滤（如：只搜均价小于100的）
            Map<String, Object> metadata = Map.of(
                    "data_type", "blog",
                    "source_id", blog.getId(),
                    "poi_id", poiId,
                    "poi_name", poiName,
                    "avg_price", poi != null && poi.getAvgPrice() != null ? poi.getAvgPrice() : 0
            );

            documents.add(new Document(text, metadata));
        }

        // 文本切分
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(600) // 考虑到前缀变长了，ChunkSize 稍微调大一点
                .withMinChunkSizeChars(100)
                .withKeepSeparator(true)
                .build();
        List<Document> chunkedDocs = splitter.apply(documents);

        log.info("开始向量化并写入 Milvus，共计 {} 个文本块...", chunkedDocs.size());
        vectorStore.add(chunkedDocs);
        log.info("游记导入成功！");
    }

    private void importComments(Map<Long, Poi> poiMap) {
        log.info("正在查询 MySQL 中的评论数据...");
        List<PoiComment> comments = commentMapper.selectList(null);

        if (comments == null || comments.isEmpty()) {
            log.info("没有找到评论数据，跳过。");
            return;
        }

        List<Document> documents = new ArrayList<>();
        for (PoiComment comment : comments) {
            Long poiId = comment.getPoiId() != null ? comment.getPoiId() : 0L;
            Poi poi = poiMap.get(poiId);

            String poiInfo = buildPoiInfoString(poi);
            String poiName = poi != null ? poi.getName() : "未知景点";

            // 🚀 同样融合进评论中
            String text = String.format("这是快鱼旅行用户对【%s】的真实评价。目的地详情：%s。用户评分：【%s星】，评价内容：【%s】",
                    poiName, poiInfo, comment.getScore(), comment.getContent());

            Map<String, Object> metadata = Map.of(
                    "data_type", "comment",
                    "source_id", comment.getId(),
                    "poi_id", poiId,
                    "poi_name", poiName
            );

            documents.add(new Document(text, metadata));
        }

        log.info("开始向量化并写入 Milvus，共计 {} 条评论...", documents.size());
        vectorStore.add(documents);
        log.info("评论导入成功！");
    }

    /**
     * 辅助方法：将 POI 的详细字段拼接成自然语言句子
     */
    private String buildPoiInfoString(Poi poi) {
        if (poi == null) {
            return "无详细地址和价格信息";
        }

        // 处理数据库中的特殊字段：评分是乘 10 保存的，这里还原为真实的 1~5 分
        double realScore = poi.getScore() != null ? poi.getScore() / 10.0 : 0.0;

        return String.format("地址位于【%s】，人均消费约【%s元】，官方评分【%.1f分】，营业时间为【%s】，联系电话【%s】",
                poi.getAddress() != null ? poi.getAddress() : "未知",
                poi.getAvgPrice() != null ? poi.getAvgPrice() : "未知",
                realScore,
                poi.getOpenHours() != null ? poi.getOpenHours() : "未知",
                poi.getPhone() != null ? poi.getPhone() : "未知"
        );
    }
}