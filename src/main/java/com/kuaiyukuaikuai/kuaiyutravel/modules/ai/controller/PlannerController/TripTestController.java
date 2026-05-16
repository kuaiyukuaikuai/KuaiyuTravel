package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.controller.PlannerController;

import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.planner.TripPlan;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.planner.TripPlanRequest;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.service.planner.TripPlannerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test/trip")
public class TripTestController {

    private final TripPlannerService tripPlannerService;

    public TripTestController(TripPlannerService tripPlannerService) {
        this.tripPlannerService = tripPlannerService;
    }

    @GetMapping("/sichuan")
    public TripPlan testWesternSichuanTrip() {
        // 构建川西自驾游的测试请求
        TripPlanRequest request = new TripPlanRequest(
                "成都", // 目的地
                "2026-05-19",          // 开始日期
                "2026-05-24",          // 结束日期
                6,                     // 游玩天数
                "自然风光", // 偏好
                "经济型",     // 预算
                "自驾",                  // 交通方式
                "经济型" // 住宿要求
        );

        // 调用你的核心服务
        return tripPlannerService.generateTripPlan(request);
    }
}