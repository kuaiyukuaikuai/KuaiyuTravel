package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.config.planner;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.client.webflux.transport.WebFluxSseClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;

@Slf4j
@Component // ⚠️ 注意这里变成了 Component，不再是 Configuration
public class AmapMcpFactory {

    @Value("${amap.mcp.key}")
    private String amapKey;

    // 每次调用这个方法，都会吐出一个全新的、绝不会断线的 MCP 客户端
    public McpSyncClient createFreshClient() {
        log.info("🌐 正在与高德 MCP 服务器建立全新的按需连接...");
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofMinutes(3));

        WebClient.Builder customWebClientBuilder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("https://mcp.amap.com")
                .filter((request, next) -> {
                    URI newUri = UriComponentsBuilder.fromUri(request.url())
                            .queryParam("key", amapKey).build(true).toUri();
                    return next.exchange(ClientRequest.from(request).url(newUri).build());
                });

        WebFluxSseClientTransport transport = new WebFluxSseClientTransport.Builder(customWebClientBuilder).build();
        McpSyncClient mcpClient = McpClient.sync(transport).build();
        mcpClient.initialize();
        return mcpClient;
    }
}