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

/**
 * 高德 MCP 客户端工厂，负责按需创建 MCP 同步客户端。
 *
 * <p>每次调用 {@link #createFreshClient()} 都会创建一个全新的 MCP 连接，
 * 避免长连接超时断开问题。使用 @Component 而非 @Configuration，因为该类不定义 Bean，
 * 而是作为工厂被其他服务注入调用。</p>
 */
@Slf4j
@Component
public class AmapMcpFactory {

    @Value("${amap.mcp.key}")
    private String amapKey;

    /**
     * 创建新的 MCP 同步客户端。
     *
     * <p>每次调用生成独立连接实例，连接初始化后立即可用。
     * 响应超时时间设置为 3 分钟，以适配大模型工具调用的耗时场景。</p>
     *
     * @return 已初始化的 MCP 同步客户端
     */
    public McpSyncClient createFreshClient() {
        log.info("正在与高德 MCP 服务器建立连接");
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
