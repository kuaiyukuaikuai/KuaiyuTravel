/*
package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.tools.planner;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// MCP 连接调试运行器，用于应用启动时验证 MCP 客户端连接状态。
// 当前已注释停用。如需调试 MCP 连接，取消注释并启动应用即可。
// 该运行器会列出所有已注册的 MCP 客户端及其暴露的工具列表。
@Slf4j
@Component
public class McpDebugRunner implements CommandLineRunner {

    private final List<McpSyncClient> mcpClients;

    public McpDebugRunner(List<McpSyncClient> mcpClients) {
        this.mcpClients = mcpClients;
    }

    @Override
    public void run(String... args) {
        log.info("========================================");
        log.info("正在向高德 MCP Server 发起底层连接测试");
        try {
            if (mcpClients.isEmpty()) {
                log.warn("容器中没有找到任何 MCP 客户端");
                return;
            }

            McpSyncClient mcpClient = mcpClients.get(0);
            var tools = mcpClient.listTools().tools();

            log.info("连接成功，共拉取到 {} 个工具", tools.size());
            for (var tool : tools) {
                log.info("原始工具名: {}", tool.name());
            }
        } catch (Exception e) {
            log.error("连接或拉取工具失败，报错信息: {}", e.getMessage());
        }
        log.info("========================================");
    }
}
*/
