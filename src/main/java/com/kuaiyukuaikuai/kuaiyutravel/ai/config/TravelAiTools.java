package com.kuaiyukuaikuai.kuaiyutravel.ai.config;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component // 💡 改为普通的 Component 组件
public class TravelAiTools {

    // 💡 重点：直接写普通参数，返回普通 String。不要再套 Function 了！
    @Tool(description = "当用户询问某个城市的旅游景点、住宿、美食时调用此工具。需要提取城市名(city)和关键词(keyword)，如：康定 酒店")
    public String searchPoiTool(String city, String keyword) {

        System.out.println("🤖 AI 正在调用本地工具 -> 城市：" + city + "，关键词：" + keyword);

        return String.format("查询 %s 的 %s 结果：1. 星空露营地（评分4.8） 2. 观景客栈（评分4.5）",
                city, keyword);
    }
}