package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.service.planner;

import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.config.planner.AmapMcpFactory;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.planner.TripPlan;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.planner.TripPlanRequest;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/**
 * 旅游行程规划服务。
 *
 * <p>基于 MCP 协议调用高德地图工具，结合多 Agent 协作完成景点搜索、天气查询、酒店推荐及最终行程规划。</p>
 */
@Slf4j
@Service
public class TripPlannerService {

    private final ChatClient.Builder chatClientBuilder;
    private final AmapMcpFactory amapMcpFactory;

    /** 限制同时执行的行程规划任务数，防止耗尽 MCP 连接资源 */
    private final Semaphore planningSemaphore = new Semaphore(3, true);

    /** 单步 LLM/MCP 调用超时 */
    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(30);

    /** 整体行程规划超时 */
    private static final Duration TOTAL_TIMEOUT = Duration.ofMinutes(3);

    /**
     * 构造器，仅保留依赖注入，不预先建立 MCP 长连接。
     *
     * <p>避免启动时因 MCP Server 不可用导致服务起不来，采用按需短连接策略。</p>
     */
    public TripPlannerService(ChatClient.Builder chatClientBuilder, AmapMcpFactory amapMcpFactory) {
        this.chatClientBuilder = chatClientBuilder;
        this.amapMcpFactory = amapMcpFactory;
    }

    /**
     * 在工具列表中按名称模糊匹配目标工具。
     *
     * @param allTools 当前 MCP 会话暴露的全部工具
     * @param target   目标工具关键字（忽略下划线与大小写）
     * @return 匹配到的 ToolCallback
     * @throws IllegalStateException 未找到匹配工具时抛出
     */
    private ToolCallback findRealToolCallback(List<ToolCallback> allTools, String target) {
        String targetClean = target.replace("_", "").toLowerCase();
        return allTools.stream()
                .filter(tool -> tool.getToolDefinition().name().replace("_", "").toLowerCase().contains(targetClean))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到MCP工具: " + target));
    }

    /**
     * 生成完整旅行计划。
     *
     * <p>入口方法，负责编排 MCP 连接、多 Agent 协作及资源释放。</p>
     *
     * @param request 用户行程请求参数
     * @return 结构化行程计划
     */
    public TripPlan generateTripPlan(TripPlanRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("[AI-MONITOR] 行程规划开始. city={}, days={}", request.city(), request.days());

        // 限流：获取许可证，防止并发过高耗尽 MCP 资源
        boolean acquired = false;
        try {
            acquired = planningSemaphore.tryAcquire(TOTAL_TIMEOUT.getSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[AI-MONITOR] 行程规划限流拒绝. city={}", request.city());
                throw new BusinessException(ErrorCode.SYSTEM_RATE_LIMIT, "当前规划请求过多，请稍后再试");
            }

            // 整体超时控制：用 CompletableFuture 包装完整流程
            return CompletableFuture.supplyAsync(() -> {
                McpSyncClient mcpClient = amapMcpFactory.createFreshClient();
                try {
                    return executeWithMcp(request, mcpClient);
                } finally {
                    closeMcpClientQuietly(mcpClient);
                }
            }).orTimeout(TOTAL_TIMEOUT.getSeconds(), TimeUnit.SECONDS).join();

        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TimeoutException) {
                log.error("[AI-MONITOR] 行程规划整体超时. city={}, cost={}ms", request.city(), System.currentTimeMillis() - startTime);
                throw new BusinessException(ErrorCode.AI_PLAN_ERROR, "行程规划耗时过长，请简化需求后重试");
            }
            if (cause instanceof BusinessException) {
                throw (BusinessException) cause;
            }
            log.error("[AI-MONITOR] 行程规划异常. city={}, cost={}ms", request.city(), System.currentTimeMillis() - startTime, cause);
            throw new BusinessException(ErrorCode.AI_PLAN_ERROR, "行程规划失败: " + cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_PLAN_ERROR, "行程规划被中断");
        } finally {
            if (acquired) {
                planningSemaphore.release();
            }
            long cost = System.currentTimeMillis() - startTime;
            log.info("[AI-MONITOR] 行程规划结束. city={}, cost={}ms", request.city(), cost);
        }
    }

    /**
     * 在 MCP 连接上下文中执行完整的规划流程。
     *
     * @param request   用户行程请求参数
     * @param mcpClient 已建立的 MCP 同步客户端
     * @return 结构化行程计划
     */
    private TripPlan executeWithMcp(TripPlanRequest request, McpSyncClient mcpClient) {
        List<ToolCallback> allTools = List.of(new SyncMcpToolCallbackProvider(mcpClient).getToolCallbacks());
        ToolCallback textSearchTool = findRealToolCallback(allTools, "maps_text_search");
        ToolCallback weatherTool = findRealToolCallback(allTools, "maps_weather");

        ChatClient baseClient = chatClientBuilder.build();

        ChatClient attractionAgent = createAttractionAgent(baseClient, textSearchTool);
        ChatClient weatherAgent = createWeatherAgent(baseClient, weatherTool);
        ChatClient hotelAgent = createHotelAgent(baseClient, textSearchTool);
        ChatClient plannerAgent = createPlannerAgent(baseClient);

        // 1. 景点搜索：核心数据，失败直接中断
        String attractions = fetchAttractions(attractionAgent, request);

        // 2. 天气查询：非核心，失败时使用兜底策略
        String weather = fetchWeatherWithFallback(weatherAgent, request);

        // 3. 酒店推荐：非核心，失败时使用兜底策略
        String hotels = fetchHotelsWithFallback(hotelAgent, request, attractions);

        // 4. 统筹规划：核心步骤，带自动重试
        String plannerPrompt = buildPlannerPrompt(request, attractions, weather, hotels);
        log.info("正在交由 PlannerAgent 进行最终统筹整合与计算...");

        return callPlannerAgent(plannerAgent, plannerPrompt);
    }

    /* ==================== Agent 工厂方法 ==================== */

    private ChatClient createAttractionAgent(ChatClient baseClient, ToolCallback textSearchTool) {
        return baseClient.mutate()
                .defaultSystem("你是景点搜索专家。必须使用工具搜索，不要编造信息。")
                .defaultToolCallbacks(textSearchTool)
                .build();
    }

    private ChatClient createWeatherAgent(ChatClient baseClient, ToolCallback weatherTool) {
        return baseClient.mutate()
                .defaultSystem("你是天气查询专家。请使用工具查询天气。")
                .defaultToolCallbacks(weatherTool)
                .build();
    }

    private ChatClient createHotelAgent(ChatClient baseClient, ToolCallback textSearchTool) {
        return baseClient.mutate()
                .defaultSystem("你是酒店推荐专家。请使用工具搜索酒店。")
                .defaultToolCallbacks(textSearchTool)
                .build();
    }

    private ChatClient createPlannerAgent(ChatClient baseClient) {
        return baseClient.mutate()
                .defaultSystem("""
                        你是首席行程规划专家。请根据提供的景点、天气和酒店信息，生成合理的旅行计划。
                        要求：
                        1. 每天安排合理的景点数量，考虑交通和游览时间。
                        2. 结合天气情况给出穿衣和出行建议。
                        3. 必须严格遵守以下 JSON Schema 输出，字段名必须与 schema 完全一致：

                        {
                          "city": "目的地城市",
                          "start_date": "YYYY-MM-DD",
                          "end_date": "YYYY-MM-DD",
                          "days": [
                            {
                              "date": "YYYY-MM-DD",
                              "day_index": 0,
                              "description": "当日行程规划与交通描述",
                              "attractions": [
                                {
                                  "name": "景点名称",
                                  "address": "详细地址",
                                  "location": {"longitude": 116.397128, "latitude": 39.916527},
                                  "visit_duration": 120,
                                  "description": "景点特色描述",
                                  "ticket_price": 80,
                                  "image_url": "图片URL（无则留空字符串）"
                                }
                              ]
                            }
                          ],
                          "weather_info": [
                            {
                              "date": "YYYY-MM-DD",
                              "day_weather": "晴",
                              "day_temp": 25,
                              "night_temp": 15
                            }
                          ],
                          "overall_suggestions": "总体旅行建议和注意事项",
                          "budget": {
                            "total_attractions": 200,
                            "total_hotels": 800,
                            "total_meals": 300,
                            "total_transportation": 150,
                            "total": 1450
                          }
                        }

                        特别注意：
                        - visit_duration 为大于0的整数（分钟）
                        - ticket_price、day_temp、night_temp 为整数
                        - budget 中所有金额均为整数（元）
                        - image_url 若高德无返回则必须填空字符串 ""
                        """)
                .build();
    }

    /* ==================== 数据获取方法 ==================== */

    /**
     * 搜索目标城市的景点信息。
     *
     * <p>景点数据是后续规划的核心输入，因此不捕获异常，失败即中断流程。</p>
     */
    private String fetchAttractions(ChatClient attractionAgent, TripPlanRequest request) {
        long stepStart = System.currentTimeMillis();
        String prompt = String.format("请根据用户偏好【%s】，搜索【%s】的景点", request.preferences(), request.city());
        String attractions = callWithTimeout("景点搜索", () -> attractionAgent.prompt().user(prompt).call().content());
        log.info("[AI-MONITOR] 景点搜索完成. cost={}ms", System.currentTimeMillis() - stepStart);
        return attractions;
    }

    /**
     * 查询天气，失败时返回兜底文案。
     *
     * <p>天气为非核心数据，避免单点故障导致整体规划失败。</p>
     */
    private String fetchWeatherWithFallback(ChatClient weatherAgent, TripPlanRequest request) {
        try {
            long stepStart = System.currentTimeMillis();
            String prompt = String.format("请查询【%s】未来几天的天气预报", request.city());
            String weather = callWithTimeout("天气查询", () -> weatherAgent.prompt().user(prompt).call().content());
            log.info("[AI-MONITOR] 天气查询完成. cost={}ms", System.currentTimeMillis() - stepStart);
            return weather;
        } catch (Exception e) {
            log.warn("[AI-MONITOR] 天气查询失败，触发兜底策略。原因: {}", e.getMessage());
            return "未获取到详细天气数据。请在规划中提示用户根据当地当季常规气候准备衣物，温度字段统一填0。";
        }
    }

    /**
     * 推荐酒店，失败时返回兜底文案。
     *
     * <p>酒店为非核心数据，避免单点故障导致整体规划失败。</p>
     */
    private String fetchHotelsWithFallback(ChatClient hotelAgent, TripPlanRequest request, String attractions) {
        try {
            long stepStart = System.currentTimeMillis();
            String prompt = buildHotelPrompt(request, attractions);
            String hotels = callWithTimeout("酒店搜索", () -> hotelAgent.prompt().user(prompt).call().content());
            log.info("[AI-MONITOR] 酒店搜索完成. cost={}ms", System.currentTimeMillis() - stepStart);
            return hotels;
        } catch (Exception e) {
            log.warn("[AI-MONITOR] 酒店查询失败，触发兜底策略。原因: {}", e.getMessage());
            return String.format(
                    "未查询到具体酒店。请在规划中安排常规的%s住宿，并按照该城市的平均消费水平估算酒店预算总金额。",
                    request.accommodation());
        }
    }

    /* ==================== Prompt 构建方法 ==================== */

    /**
     * 构建酒店推荐提示词。
     *
     * <p>将用户计划游玩的景点作为上下文传入，使推荐结果更具地理位置相关性。</p>
     *
     * @param request    用户行程请求
     * @param attractions 已搜索到的景点信息
     * @return 酒店推荐提示词
     */
    private String buildHotelPrompt(TripPlanRequest request, String attractions) {
        return String.format("""
                        请搜索【%s】的【%s】酒店。
                        重要约束：请阅读以下用户即将游玩的景点，推荐距离这些景点较近、或者处于景点路线中心枢纽位置的酒店：
                        【用户计划游玩的景点】
                        %s
                        """,
                request.city(), request.accommodation(), attractions);
    }

    /**
     * 构建最终行程规划提示词。
     *
     * <p>将用户原始需求与各领域专家收集的信息整合为统一输入，供 PlannerAgent 生成结构化计划。</p>
     *
     * @param request    用户行程请求
     * @param attractions 景点信息
     * @param weather    天气信息
     * @param hotels     酒店信息
     * @return 统筹规划提示词
     */
    private String buildPlannerPrompt(TripPlanRequest request, String attractions, String weather, String hotels) {
        return String.format("""
                        请为用户生成旅行计划。

                        **用户原始需求:**
                        - 目的地: %s
                        - 日期: %s 至 %s
                        - 偏好: %s
                        - 预算级别: %s
                        - 交通方式: %s

                        **各领域专家获取的最新信息:**
                        【景点信息】
                        %s

                        【天气信息】
                        %s

                        【酒店信息】
                        %s

                        **特别要求（非常重要）：**
                        1. 必须从高德获取的景点数据中，提取图片URL并填充到 attractions 的 image_url 字段中。如果没有则留空。
                        2. 必须计算预算(budget)！请根据景点的常规门票、用户选择的【%s】预算级别预估酒店和餐饮费用、交通费用，并计算出各项明细和总价。
                        3. 温度字段必须提取为纯数字。
                        """,
                request.city(), request.startDate(), request.endDate(),
                request.preferences(), request.budget(), request.transportation(),
                attractions, weather, hotels, request.budget());
    }

    /* ==================== Agent 调用方法 ==================== */

    /**
     * 调用规划 Agent 生成行程，带自动重试机制。
     *
     * <p>LLM 输出 JSON 时可能出现格式异常，通过有限重试提升成功率。</p>
     *
     * @param plannerAgent 行程规划 Agent
     * @param plannerPrompt 统筹规划提示词
     * @return 解析后的结构化行程计划
     * @throws BusinessException 达到最大重试次数后仍失败时抛出
     */
    private TripPlan callPlannerAgent(ChatClient plannerAgent, String plannerPrompt) {
        int maxRetries = 3;
        for (int i = 1; i <= maxRetries; i++) {
            try {
                long stepStart = System.currentTimeMillis();
                TripPlan plan = callWithTimeout("统筹规划",
                        () -> plannerAgent.prompt().user(plannerPrompt).call().entity(TripPlan.class));
                log.info("[AI-MONITOR] 统筹规划完成. cost={}ms", System.currentTimeMillis() - stepStart);
                return plan;
            } catch (Exception e) {
                log.error("[AI-MONITOR] 第 {} 次 Planner 生成或 JSON 解析失败: {}", i, e.getMessage());
                if (i == maxRetries) {
                    log.error("[AI-MONITOR] 已达到最大重试次数，行程规划彻底失败。");
                    throw new BusinessException(ErrorCode.AI_PLAN_ERROR,
                            "行程规划生成失败，大模型多次返回异常格式，请稍后再试");
                }
                log.info("正在进行第 {} 次重试...", i + 1);
            }
        }
        // 逻辑上不会到达此处，仅为编译器通过
        return null;
    }

    /**
     * 带超时的 LLM 调用工具方法。
     *
     * <p>将同步阻塞的 ChatClient 调用包装在 CompletableFuture 中，防止单步调用无限期挂起。</p>
     *
     * @param stepName 步骤名称（用于日志和异常信息）
     * @param supplier 实际调用逻辑
     * @param <T>      返回类型
     * @return 调用结果
     * @throws BusinessException 超时或调用异常时抛出
     */
    private <T> T callWithTimeout(String stepName, java.util.function.Supplier<T> supplier) {
        try {
            return CompletableFuture.supplyAsync(supplier)
                    .orTimeout(STEP_TIMEOUT.getSeconds(), TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TimeoutException) {
                throw new BusinessException(ErrorCode.AI_PLAN_ERROR,
                        String.format("%s步骤超时（超过%d秒），请稍后重试", stepName, STEP_TIMEOUT.getSeconds()));
            }
            throw new BusinessException(ErrorCode.AI_PLAN_ERROR,
                    String.format("%s步骤失败: %s", stepName, cause.getMessage()));
        }
    }

    /* ==================== 资源管理方法 ==================== */

    /**
     * 静默关闭 MCP 客户端，不向上传播异常。
     *
     * <p>资源释放异常不应影响主流程结果，仅记录警告日志。</p>
     */
    private void closeMcpClientQuietly(McpSyncClient mcpClient) {
        try {
            mcpClient.close();
            log.info("行程规划结束，MCP底层连接已安全释放。");
        } catch (Exception e) {
            log.warn("释放MCP连接时出现异常 (可忽略): {}", e.getMessage());
        }
    }
}