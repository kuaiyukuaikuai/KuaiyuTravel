package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.service.planner;

import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.config.planner.AmapMcpFactory;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.planner.TripPlan;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.planner.TripPlanRequest;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class TripPlannerService {

    private final ChatClient.Builder chatClientBuilder;
    private final AmapMcpFactory amapMcpFactory;

    // 构造函数：不再预先加载 MCP 连接，只保留依赖注入
    public TripPlannerService(ChatClient.Builder chatClientBuilder, AmapMcpFactory amapMcpFactory) {
        this.chatClientBuilder = chatClientBuilder;
        this.amapMcpFactory = amapMcpFactory;
    }

    private ToolCallback findRealToolCallback(List<ToolCallback> allTools, String target) {
        String targetClean = target.replace("_", "").toLowerCase();
        return allTools.stream()
                .filter(tool -> tool.getToolDefinition().name().replace("_", "").toLowerCase().contains(targetClean))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到MCP工具: " + target));
    }

    public TripPlan generateTripPlan(TripPlanRequest request) {
        log.info("开始为用户规划前往 {} 的行程...", request.city());

        // 🌟 核心杀招：按需获取一个极其新鲜的短连接，用完就抛弃
        McpSyncClient mcpClient = amapMcpFactory.createFreshClient();

        try {
            // 提取最新连接中的工具
            List<ToolCallback> allTools = List.of(new SyncMcpToolCallbackProvider(mcpClient).getToolCallbacks());
            ToolCallback textSearchTool = findRealToolCallback(allTools, "maps_text_search");
            ToolCallback weatherTool = findRealToolCallback(allTools, "maps_weather");

            // 动态组装属于当前请求的专属 Agents
            ChatClient baseClient = chatClientBuilder.build();

            ChatClient attractionAgent = baseClient.mutate()
                    .defaultSystem("你是景点搜索专家。必须使用工具搜索，不要编造信息。")
                    .defaultToolCallbacks(textSearchTool)
                    .build();

            ChatClient weatherAgent = baseClient.mutate()
                    .defaultSystem("你是天气查询专家。请使用工具查询天气。")
                    .defaultToolCallbacks(weatherTool)
                    .build();

            ChatClient hotelAgent = baseClient.mutate()
                    .defaultSystem("你是酒店推荐专家。请使用工具搜索酒店。")
                    .defaultToolCallbacks(textSearchTool)
                    .build();

            ChatClient plannerAgent = baseClient.mutate()
                    .defaultSystem("""
                            你是首席行程规划专家。请根据提供的景点、天气和酒店信息，生成合理的旅行计划。
                            要求：
                            1. 每天安排合理的景点数量，考虑交通和游览时间。
                            2. 结合天气情况给出穿衣和出行建议。
                            3. 必须严格遵守预期的 JSON 结构输出。
                            """)
                    .build();

            // ---------- 执行业务逻辑 ----------

            // 1. 景点搜索 (核心数据，如果失败直接抛出异常，这里假设高德此接口最稳定)
            String attractionPrompt = String.format("请根据用户偏好【%s】，搜索【%s】的景点", request.preferences(), request.city());
            String attractions = attractionAgent.prompt().user(attractionPrompt).call().content();
            log.debug("景点搜索完成: {}", attractions);

            // 2. 天气查询 (非核心，加入兜底机制)
            String weather;
            try {
                String weatherPrompt = String.format("请查询【%s】未来几天的天气预报", request.city());
                weather = weatherAgent.prompt().user(weatherPrompt).call().content();
                log.debug("天气查询完成: {}", weather);
            } catch (Exception e) {
                log.warn("🌤️ 天气查询失败，触发兜底策略。原因: {}", e.getMessage());
                // 兜底数据：告诉 PlannerAgent 天气缺失，让它按常识规划
                weather = "未获取到详细天气数据。请在规划中提示用户根据当地当季常规气候准备衣物，温度字段统一填0。";
            }

            // 3. 酒店推荐 (非核心，加入兜底机制)
            String hotels;
            try {
                String hotelPrompt = String.format("""
                        请搜索【%s】的【%s】酒店。
                        ⚠️ 重要约束：请阅读以下用户即将游玩的景点，推荐距离这些景点较近、或者处于景点路线中心枢纽位置的酒店：
                        【用户计划游玩的景点】
                        %s
                        """, request.city(), request.accommodation(), attractions);
                hotels = hotelAgent.prompt().user(hotelPrompt).call().content();
                log.debug("酒店推荐完成: {}", hotels);
            } catch (Exception e) {
                log.warn("🏨 酒店查询失败，触发兜底策略。原因: {}", e.getMessage());
                // 兜底数据：告诉 PlannerAgent 酒店缺失，让它按预算盲算
                hotels = String.format("未查询到具体酒店。请在规划中安排常规的%s住宿，并按照该城市的平均消费水平估算酒店预算总金额。", request.accommodation());
            }

            // 4. 统筹规划 (最核心步骤，加入自动重试机制)
            String plannerPrompt = String.format("""
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

            log.info("正在交由 PlannerAgent 进行最终统筹整合与计算...");

            // 🌟 核心杀招：最多重试 3 次，防止 LLM 输出的 JSON 格式崩坏
            int maxRetries = 3;
            for (int i = 1; i <= maxRetries; i++) {
                try {
                    return plannerAgent.prompt()
                            .user(plannerPrompt)
                            .call()
                            .entity(TripPlan.class);
                } catch (Exception e) {
                    log.error("❌ 第 {} 次 Planner 生成或 JSON 解析失败: {}", i, e.getMessage());
                    if (i == maxRetries) {
                        log.error("已达到最大重试次数，行程规划彻底失败。");
                        throw new RuntimeException("行程规划生成失败，大模型多次返回异常格式，请稍后再试。", e);
                    }
                    log.info("🔄 正在进行第 {} 次重试...", i + 1);
                }
            }

            return null; // 正常情况不会走到这里

        } finally {
            // 🌟 无论代码成功还是抛出异常，强制关闭网络资源
            try {
                mcpClient.close();
                log.info("🔌 行程规划结束，MCP底层连接已安全释放。");
            } catch (Exception e) {
                log.warn("释放MCP连接时出现异常 (可忽略): {}", e.getMessage());
            }
        }
    }
}