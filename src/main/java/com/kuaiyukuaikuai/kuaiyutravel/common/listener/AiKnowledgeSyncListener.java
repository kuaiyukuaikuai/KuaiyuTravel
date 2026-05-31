package com.kuaiyukuaikuai.kuaiyutravel.common.listener;

import com.kuaiyukuaikuai.kuaiyutravel.common.config.RabbitMQConfig;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
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
 * AI 知识库同步监听器，基于 MQ 削峰填谷实现向量化数据入库。
 *
 * <p>监听游记与评论的变更事件，将数据向量化后写入 Milvus，供 RAG 检索使用。</p>
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
     * 监听游记同步队列，实现削峰填谷式的向量化入库。
     *
     * <p>使用 {@code @QueueBinding} 注解，Spring Boot 启动时自动创建队列并绑定到交换机。</p>
     *
     * @param blogId 游记 ID
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = RabbitMQConfig.AI_SYNC_QUEUE, durable = "true"),
            exchange = @Exchange(name = RabbitMQConfig.BLOG_EXCHANGE),
            key = RabbitMQConfig.AI_SYNC_ROUTING_KEY
    ))
    public void listenBlogSyncToMilvus(Long blogId) {
        log.info("接收到 MQ 消息，开始同步游记入 Milvus，游记 ID: {}", blogId);

        try {
            // 1. 查询游记数据（MQ 消费时事务已提交，确保获取到最新数据）
            Blog blog = blogMapper.selectById(blogId);
            if (blog == null) {
                log.warn("游记 ID {} 不存在，可能已被删除，跳过同步", blogId);
                return;
            }

            // 2. 查询关联 POI 数据，扩展文本维度
            Long poiId = blog.getPoiId() != null ? blog.getPoiId() : 0L;
            Poi poi = poiMapper.selectById(poiId);

            String poiInfo = buildPoiInfoString(poi);
            String poiName = poi != null ? poi.getName() : "未知景点";

            // 3. 组装文本供大模型处理
            String text = String.format("这是一篇关于【%s】的快鱼旅行用户最新游记。该目的地详情如下：%s。游记标题：【%s】，内容：【%s】",
                    poiName, poiInfo, blog.getTitle(), blog.getContent());

            // 4. 构建元数据标签，用于后续精准检索
            Map<String, Object> metadata = Map.of(
                    "data_type", "blog",
                    "source_id", blog.getId(),
                    "poi_id", poiId,
                    "poi_name", poiName,
                    "avg_price", poi != null && poi.getAvgPrice() != null ? poi.getAvgPrice() : 0
            );

            // 5. 文本切分与向量化入库
            Document document = new Document(text, metadata);
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(600)
                    .withMinChunkSizeChars(100)
                    .withKeepSeparator(true)
                    .build();

            List<Document> chunkedDocs = splitter.apply(List.of(document));

            vectorStore.add(chunkedDocs);

            log.info("游记 ID: {} 已成功向量化并存入 Milvus RAG 知识库", blogId);

        } catch (Exception e) {
            // 网络波动等异常触发 RabbitMQ 自动重试
            log.error("同步游记到 Milvus 失败，游记 ID: {}", blogId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "AI 同步失败，触发 MQ 重试机制");
        }
    }

    /**
     * 将 POI 数据转换为自然语言描述。
     *
     * @param poi POI 实体
     * @return POI 的自然语言描述字符串
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
     * 监听评论同步队列，将评论数据向量化入库。
     *
     * @param commentId 评论 ID
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = RabbitMQConfig.AI_SYNC_COMMENT_QUEUE, durable = "true"),
            exchange = @Exchange(name = RabbitMQConfig.BLOG_EXCHANGE),
            key = RabbitMQConfig.AI_SYNC_COMMENT_ROUTING_KEY
    ))
    public void listenCommentSyncToMilvus(Long commentId) {
        log.info("接收到 MQ 消息，开始同步评论入 Milvus，评论 ID: {}", commentId);

        try {
            // 1. 查询评论数据
            com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.PoiComment comment = commentMapper.selectById(commentId);
            if (comment == null) {
                log.warn("评论 ID {} 不存在，可能已被删除，跳过同步", commentId);
                return;
            }

            // 2. 查询关联 POI 数据，扩展文本维度
            Long poiId = comment.getPoiId() != null ? comment.getPoiId() : 0L;
            Poi poi = poiMapper.selectById(poiId);

            String poiInfo = buildPoiInfoString(poi);
            String poiName = poi != null ? poi.getName() : "未知景点";

            // 3. 组装文本供大模型处理
            String text = String.format("这是快鱼旅行用户对【%s】景点的真实评价。该目的地详情如下：%s。用户评分：【%s星】，评价内容：【%s】",
                    poiName, poiInfo, comment.getScore(), comment.getContent());

            // 4. 构建元数据标签，区分评论类型
            Map<String, Object> metadata = Map.of(
                    "data_type", "comment",
                    "source_id", comment.getId(),
                    "poi_id", poiId,
                    "poi_name", poiName
            );

            // 5. 文本切分与向量化入库
            Document document = new Document(text, metadata);
            TokenTextSplitter splitter = TokenTextSplitter.builder().build();
            List<Document> chunkedDocs = splitter.apply(List.of(document));

            vectorStore.add(chunkedDocs);

            log.info("评论 ID: {} 已成功向量化并存入 Milvus RAG 知识库", commentId);

        } catch (Exception e) {
            log.error("同步评论到 Milvus 失败，评论 ID: {}", commentId, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "AI 评论同步失败，触发 MQ 重试机制");
        }
    }
}
