package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.controller.PlannerController;

import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.planner.TripPlan;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.planner.TripPlanRequest;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.service.planner.TripPlannerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/trip")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class TripController {

    private final TripPlannerService tripPlannerService;

    public TripController(TripPlannerService tripPlannerService) {
        this.tripPlannerService = tripPlannerService;
    }

    /**
     * 接收前端表单数据，动态生成旅行计划
     */
    @PostMapping("/plan")
    public TripPlan generateTripPlan(@RequestBody TripPlanRequest request) {
        log.info("收到行程规划请求: 目的地={}, 日期={}至{}",
                request.city(), request.startDate(), request.endDate());

        // 调用核心服务，开始 Agent 协作推演
        return tripPlannerService.generateTripPlan(request);
    }
}