package com.kuaiyukuaikuai.kuaiyutravel.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 快鱼旅行全局跨域配置类
 * 保障前端对【AI/对话模块】、【POI模块】等核心 API 的跨域调用
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 映射所有后端接口路径
        registry.addMapping("/**")
                // 允许跨域请求的域名（本地开发环境设为 *，生产环境务必替换为实际前端域名）
                .allowedOriginPatterns("*")
                // 允许携带请求凭证（如 Token，保障【我的模块】中订单、智能设置的用户身份识别）
                .allowCredentials(true)
                // 允许的 HTTP 方法，包含 OPTIONS 用于处理复杂请求的预检
                .allowedMethods(
                        HttpMethod.GET.name(), 
                        HttpMethod.POST.name(), 
                        HttpMethod.PUT.name(), 
                        HttpMethod.DELETE.name(), 
                        HttpMethod.OPTIONS.name()
                )
                // 允许的请求头
                .allowedHeaders("*")
                // 预检请求的缓存时间（单位：秒），避免频繁发送 OPTIONS 请求降低性能
                .maxAge(3600);
    }
}