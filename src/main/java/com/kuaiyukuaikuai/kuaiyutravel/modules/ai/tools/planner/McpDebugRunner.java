package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.tools.planner;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class McpDebugRunner implements CommandLineRunner {

    // 【关键修改】接收 List，因为 Spring 会把所有的 MCP 客户端打包成集合
    private final List<McpSyncClient> mcpClients;

    public McpDebugRunner(List<McpSyncClient> mcpClients) {
        this.mcpClients = mcpClients;
    }

    @Override
    public void run(String... args) {
        log.info("========================================");
        log.info("🚀 正在向高德 MCP Server 发起底层连接测试...");
        try {
            if (mcpClients.isEmpty()) {
                log.warn("❌ 容器中没有找到任何 MCP 客户端！");
                return;
            }

            // 我们目前只配了一个高德，所以直接取第 0 个
            McpSyncClient mcpClient = mcpClients.get(0);
            var tools = mcpClient.listTools().tools();

            log.info("✅ 连接成功！共拉取到 {} 个工具。", tools.size());
            for (var tool : tools) {
                log.info(" 👉 原始工具名: {}", tool.name());
            }
        } catch (Exception e) {
            log.error("❌ 连接或拉取工具失败！报错信息: {}", e.getMessage());
        }
        log.info("========================================");
    }
}