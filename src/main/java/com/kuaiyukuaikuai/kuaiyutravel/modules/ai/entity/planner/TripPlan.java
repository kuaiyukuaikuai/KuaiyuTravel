package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.planner;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

// 1. 位置坐标
record Location(
        @JsonPropertyDescription("经度，例如：116.397128") double longitude,
        @JsonPropertyDescription("纬度，例如：39.916527") double latitude
) {}

// 2. 景点信息
record Attraction(
        @JsonPropertyDescription("景点名称") String name,
        @JsonPropertyDescription("景点详细地址") String address,
        @JsonPropertyDescription("经纬度坐标") Location location,

        @JsonProperty("visit_duration")
        @JsonPropertyDescription("建议游览时间(分钟)，必须是大于0的整数") int visitDuration,

        @JsonPropertyDescription("景点描述，突出特色") String description,

        @JsonProperty("ticket_price")
        @JsonPropertyDescription("预估门票价格(元)，免费景点填0") int ticketPrice,

        @JsonProperty("image_url")
        @JsonPropertyDescription("高德地图返回的景点图片URL，如果没有则留空") String imageUrl
) {}

// 3. 每日安排
record DayPlan(
        @JsonPropertyDescription("日期，例如：2026-05-19") String date,

        @JsonProperty("day_index")
        @JsonPropertyDescription("第几天(从0开始)") int dayIndex,

        @JsonPropertyDescription("当日行程规划与交通描述") String description,
        @JsonPropertyDescription("当日计划游玩的景点列表") List<Attraction> attractions
) {}

// 4. 天气信息
record WeatherInfo(
        @JsonPropertyDescription("日期，例如：2026-05-19") String date,

        @JsonProperty("day_weather")
        @JsonPropertyDescription("白天天气现象，如：晴、多云") String dayWeather,

        @JsonProperty("day_temp")
        @JsonPropertyDescription("白天温度(纯数字，摄氏度)") int dayTemp,

        @JsonProperty("night_temp")
        @JsonPropertyDescription("夜间温度(纯数字，摄氏度)") int nightTemp
) {}

// 5. 预算汇总
record Budget(
        @JsonProperty("total_attractions")
        @JsonPropertyDescription("景点门票总费用(元)") int totalAttractions,

        @JsonProperty("total_hotels")
        @JsonPropertyDescription("酒店住宿总费用(元)") int totalHotels,

        @JsonProperty("total_meals")
        @JsonPropertyDescription("餐饮总费用(元)") int totalMeals,

        @JsonProperty("total_transportation")
        @JsonPropertyDescription("交通总费用(元)") int totalTransportation,

        @JsonPropertyDescription("各项相加的预估总费用(元)") int total
) {}

/**
 * 最终的旅行计划结果
 */
public record TripPlan(
        @JsonPropertyDescription("目的地城市") String city,

        @JsonProperty("start_date")
        @JsonPropertyDescription("开始日期 YYYY-MM-DD") String startDate,

        @JsonProperty("end_date")
        @JsonPropertyDescription("结束日期 YYYY-MM-DD") String endDate,

        @JsonPropertyDescription("每日详细行程安排") List<DayPlan> days,

        @JsonProperty("weather_info")
        @JsonPropertyDescription("天气预报信息列表") List<WeatherInfo> weatherInfo,

        @JsonProperty("overall_suggestions")
        @JsonPropertyDescription("给用户的总体旅行建议和注意事项") String overallSuggestions,

        @JsonPropertyDescription("旅行整体预算预估明细") Budget budget
) {}