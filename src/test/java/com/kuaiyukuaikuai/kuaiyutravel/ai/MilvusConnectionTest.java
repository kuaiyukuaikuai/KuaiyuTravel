package com.kuaiyukuaikuai.kuaiyutravel.ai;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

/**
 * Milvus 向量数据库连通性与 RAG 基础读写测试
 */
@SpringBootTest
public class MilvusConnectionTest {

    // 只要 YAML 配置正确，Spring Boot 会自动帮你实例化 MilvusVectorStore
    @Resource
    private VectorStore vectorStore;

    @Test
    public void testMilvusReadAndWrite() {
        System.out.println("========== 1. 开始测试写入 Milvus ==========");
        
        // 模拟一条极其优质的本地游记数据
        String text = "快鱼旅行测试：成都春熙路有一家叫做【蜀大侠】的火锅店，牛油锅底非常地道，而且人均只要100元，强烈推荐给去成都旅游的朋友！";
        
        // 构建带有元数据的 Document
        Document doc = new Document(text, Map.of(
                "source", "kuaiyu_test_blog",
                "city", "成都",
                "poi", "蜀大侠火锅"
        ));

        try {
            // 这行代码会先调用 硅基流动的 BAAI/bge-m3 模型将 text 变成 1024 维度的向量，然后通过网络存入阿里云的 Milvus
            vectorStore.add(List.of(doc));
            System.out.println("✅ 写入成功！数据已转化为向量并落入阿里云 Milvus！");
        } catch (Exception e) {
            System.err.println("❌ 写入失败！请检查 API Key 是否正确，或阿里云 19530 端口是否放行。");
            e.printStackTrace();
            return;
        }

        System.out.println("\n========== 2. 开始测试相似度检索 (RAG) ==========");
        try {
            // 模拟用户在问大模型："成都有什么好吃的火锅？"
            String userQuery = "成都有什么好吃的火锅？";
            System.out.println("用户提问: " + userQuery);

            // 让 Milvus 找出最相关的 1 条数据 (topK = 1)
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder().query(userQuery).topK(1).build()
            );

            System.out.println("✅ 检索完成！匹配到 " + results.size() + " 条结果：");
            for (Document result : results) {
                System.out.println("👉 召回内容: " + result.getText());
                System.out.println("👉 元数据: " + result.getMetadata());
            }
        } catch (Exception e) {
            System.err.println("❌ 检索失败！");
            e.printStackTrace();
        }
    }
}