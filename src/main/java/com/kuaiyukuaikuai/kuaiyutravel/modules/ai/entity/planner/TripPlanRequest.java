package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.planner;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 旅行规划请求
 */
public record TripPlanRequest(
        @NotBlank(message = "目的地不能为空")
        @Size(max = 50, message = "目的地名称长度不能超过50个字符")
        String city,

        @JsonProperty("start_date")
        @NotBlank(message = "开始日期不能为空")
        String startDate,

        @JsonProperty("end_date")
        @NotBlank(message = "结束日期不能为空")
        String endDate,

        @Min(value = 1, message = "旅行天数至少为1天")
        @Max(value = 30, message = "旅行天数最多为30天")
        int days,

        @Size(max = 100, message = "偏好描述长度不能超过100个字符")
        String preferences,

        @Size(max = 50, message = "预算描述长度不能超过50个字符")
        String budget,

        @Size(max = 50, message = "交通方式描述长度不能超过50个字符")
        String transportation,

        @Size(max = 50, message = "住宿要求描述长度不能超过50个字符")
        String accommodation
) {}
