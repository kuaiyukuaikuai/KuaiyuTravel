package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.planner;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 旅行规划请求
 */
public record TripPlanRequest(
        String city,           // 目的地

        @JsonProperty("start_date")
        String startDate,      // 开始日期

        @JsonProperty("end_date")
        String endDate,        // 结束日期

        int days,              // 天数
        String preferences,    // 偏好（如：历史文化、自然风光）
        String budget,         // 预算（如：经济型、豪华型）
        String transportation, // 交通方式
        String accommodation   // 住宿要求
) {}