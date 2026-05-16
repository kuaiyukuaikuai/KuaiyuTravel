package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.service.planner;

import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.planner.TripPlan;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.planner.TripPlanRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TripPlannerService {

    private final ChatClient attractionAgent;
    private final ChatClient weatherAgent;
    private final ChatClient hotelAgent;
    private final ChatClient plannerAgent;

    public TripPlannerService(ChatClient.Builder builder, List<ToolCallbackProvider> providers) {

        List<ToolCallback> allTools = new ArrayList<>();
        providers.forEach(provider -> {
            allTools.addAll(List.of(provider.getToolCallbacks()));
        });
        log.info("🎯 系统拉取到真实的 MCP 工具数量: {}", allTools.size());

        ToolCallback textSearchTool = findRealToolCallback(allTools, "maps_text_search");
        ToolCallback weatherTool = findRealToolCallback(allTools, "maps_weather");

        ChatClient baseClient = builder.build();

        this.attractionAgent = baseClient.mutate()
                .defaultSystem("你是景点搜索专家。必须使用工具搜索，不要编造信息。")
                .defaultToolCallbacks(textSearchTool)
                .build();

        this.weatherAgent = baseClient.mutate()
                .defaultSystem("你是天气查询专家。请使用工具查询天气。")
                .defaultToolCallbacks(weatherTool)
                .build();

        this.hotelAgent = baseClient.mutate()
                .defaultSystem("你是酒店推荐专家。请使用工具搜索酒店。")
                .defaultToolCallbacks(textSearchTool)
                .build();

        this.plannerAgent = baseClient.mutate()
                .defaultSystem("""
                        你是首席行程规划专家。请根据提供的景点、天气和酒店信息，生成合理的旅行计划。
                        要求：
                        1. 每天安排合理的景点数量，考虑交通和游览时间。
                        2. 结合天气情况给出穿衣和出行建议。
                        3. 必须严格遵守预期的 JSON 结构输出。
                        """)
                .build();
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

        // 步骤 1: 景点搜索
        String attractionPrompt = String.format("请根据用户偏好【%s】，搜索【%s】的景点", request.preferences(), request.city());
        String attractions = attractionAgent.prompt().user(attractionPrompt).call().content();
        log.debug("景点搜索完成: {}", attractions);

        // 步骤 2: 天气查询
        String weatherPrompt = String.format("请查询【%s】未来几天的天气预报", request.city());
        String weather = weatherAgent.prompt().user(weatherPrompt).call().content();
        log.debug("天气查询完成: {}", weather);

        // 步骤 3: 酒店推荐 (🔥核心改进：把景点信息喂给酒店Agent，防止酒店订得太远)
        String hotelPrompt = String.format("""
                请搜索【%s】的【%s】酒店。
                ⚠️ 重要约束：请阅读以下用户即将游玩的景点，推荐距离这些景点较近、或者处于景点路线中心枢纽位置的酒店：
                【用户计划游玩的景点】
                %s
                """, request.city(), request.accommodation(), attractions);
        String hotels = hotelAgent.prompt().user(hotelPrompt).call().content();
        log.debug("酒店推荐完成: {}", hotels);

        // 步骤 4: 行程规划与 JSON 结构化提取 (🔥核心改进：增加提取图片和计算预算的明确指令)
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

        return plannerAgent.prompt()
                .user(plannerPrompt)
                .call()
                .entity(TripPlan.class);
    }
}