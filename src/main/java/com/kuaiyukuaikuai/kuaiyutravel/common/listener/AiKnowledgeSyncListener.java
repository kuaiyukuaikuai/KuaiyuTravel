package com.kuaiyukuaikuai.kuaiyutravel.common.listener;

import com.kuaiyukuaikuai.kuaiyutravel.common.config.RabbitMQConfig;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Blog;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.BlogMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.PoiCommentMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper.PoiMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 纯 MQ 方案：AI 知识库同步消费者
 */
@Slf4j
@Component
public class AiKnowledgeSyncListener {

    @Resource
    private VectorStore vectorStore;

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private PoiMapper poiMapper;

    @Resource
    private PoiCommentMapper commentMapper;

    /**
     * 监听 AI 同步队列，实现削峰填谷式的向量化入库
     * (这里使用了 @QueueBinding 注解，Spring Boot 启动时会自动帮你创建队列并绑定到交换机上)
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = RabbitMQConfig.AI_SYNC_QUEUE, durable = "true"),
            exchange = @Exchange(name = RabbitMQConfig.BLOG_EXCHANGE),
            key = RabbitMQConfig.AI_SYNC_ROUTING_KEY
    ))
    public void listenBlogSyncToMilvus(Long blogId) {
        log.info("📥 接收到 MQ 消息，开始同步游记入 Milvus，游记 ID: {}", blogId);

        try {
            // 1. 查出刚保存的游记 (由于是从 MQ 获取，这里查出来的数据绝对是事务提交后的最新数据)
            Blog blog = blogMapper.selectById(blogId);
            if (blog == null) {
                log.warn("游记 ID {} 不存在，可能已被删除，跳过同步。", blogId);
                return;
            }

            // 2. 查出关联的 POI，进行【数据扩维】
            Long poiId = blog.getPoiId() != null ? blog.getPoiId() : 0L;
            Poi poi = poiMapper.selectById(poiId);

            String poiInfo = buildPoiInfoString(poi);
            String poiName = poi != null ? poi.getName() : "未知景点";

            // 3. 组装丰满的文本给大模型
            String text = String.format("这是一篇关于【%s】的快鱼旅行用户最新游记。该目的地详情如下：%s。游记标题：【%s】，内容：【%s】",
                    poiName, poiInfo, blog.getTitle(), blog.getContent());

            // 4. 打上元数据标签 (极度重要：后续精准检索就靠它)
            Map<String, Object> metadata = Map.of(
                    "data_type", "blog",
                    "source_id", blog.getId(),
                    "poi_id", poiId,
                    "poi_name", poiName,
                    "avg_price", poi != null && poi.getAvgPrice() != null ? poi.getAvgPrice() : 0
            );

            // 5. 文本切分与向量化入库
            Document document = new Document(text, metadata);
            // 文本切分
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(600) // 考虑到前缀变长了，ChunkSize 稍微调大一点
                    .withMinChunkSizeChars(100)
                    .withKeepSeparator(true)
                    .build();

/*            // 文本切分
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(600) // 考虑到前缀变长了，ChunkSize 稍微调大一点
                    .withMinChunkSizeChars(100)
                    .withKeepSeparator(true)
                    .build();*/



            List<Document> chunkedDocs = splitter.apply(List.of(document));

            vectorStore.add(chunkedDocs); // 这一步会调用硅基流动大模型 API，并写入 Milvus
            
            log.info("✅ 游记 ID: {} 已成功向量化并存入 Milvus RAG 知识库！", blogId);

        } catch (Exception e) {
            // 如果遇到网络波动导致向量化失败，抛出异常，RabbitMQ 会自动重试！
            log.error("❌ 同步游记到 Milvus 失败！游记 ID: {}", blogId, e);
            throw new RuntimeException("AI 同步失败，触发 MQ 重试机制", e);
        }
    }

    /**
     * 辅助方法：POI 数据转自然语言
     */
    private String buildPoiInfoString(Poi poi) {
        if (poi == null) return "无详细地址和价格信息";
        double realScore = poi.getScore() != null ? poi.getScore() / 10.0 : 0.0;
        return String.format("地址位于【%s】，人均消费约【%s元】，官方评分【%.1f分】，营业时间为【%s】",
                poi.getAddress() != null ? poi.getAddress() : "未知",
                poi.getAvgPrice() != null ? poi.getAvgPrice() : "未知",
                realScore,
                poi.getOpenHours() != null ? poi.getOpenHours() : "未知"
        );
    }


    /**
     * 🚀 新增：监听评论同步队列
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = RabbitMQConfig.AI_SYNC_COMMENT_QUEUE, durable = "true"),
            exchange = @Exchange(name = RabbitMQConfig.BLOG_EXCHANGE),
            key = RabbitMQConfig.AI_SYNC_COMMENT_ROUTING_KEY
    ))
    public void listenCommentSyncToMilvus(Long commentId) {
        log.info("📥 接收到 MQ 消息，开始同步【评论】入 Milvus，评论 ID: {}", commentId);

        try {
            // 1. 查出刚保存的评论
            com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.PoiComment comment = commentMapper.selectById(commentId);
            if (comment == null) {
                log.warn("评论 ID {} 不存在，可能已被删除，跳过同步。", commentId);
                return;
            }

            // 2. 查出关联的 POI，进行【数据扩维】
            Long poiId = comment.getPoiId() != null ? comment.getPoiId() : 0L;
            Poi poi = poiMapper.selectById(poiId);

            String poiInfo = buildPoiInfoString(poi); // 复用之前的辅助方法
            String poiName = poi != null ? poi.getName() : "未知景点";

            // 3. 组装丰满的文本给大模型
            String text = String.format("这是快鱼旅行用户对【%s】景点的真实评价。该目的地详情如下：%s。用户评分：【%s星】，评价内容：【%s】",
                    poiName, poiInfo, comment.getScore(), comment.getContent());

            // 4. 打上元数据标签 (极度重要：后续精准检索就靠它)
            Map<String, Object> metadata = Map.of(
                    "data_type", "comment", // 🌟 区分这是评论
                    "source_id", comment.getId(),
                    "poi_id", poiId,
                    "poi_name", poiName
            );

            // 5. 文本切分与向量化入库
            Document document = new Document(text, metadata);
            TokenTextSplitter splitter = TokenTextSplitter.builder().build();
            List<Document> chunkedDocs = splitter.apply(List.of(document));

            vectorStore.add(chunkedDocs);

            log.info("✅ 评论 ID: {} 已成功向量化并存入 Milvus RAG 知识库！", commentId);

        } catch (Exception e) {
            log.error("❌ 同步评论到 Milvus 失败！评论 ID: {}", commentId, e);
            throw new RuntimeException("AI 评论同步失败，触发 MQ 重试机制", e);
        }
    }
}