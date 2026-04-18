package com.kuaiyukuaikuai.kuaiyutravel.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置属性类
 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliOssProperties {
    
    /**
     * OSS 服务地址（如：oss-cn-hangzhou.aliyuncs.com）
     */
    private String endpoint;
    
    /**
     * 访问密钥 ID
     */
    private String accessKeyId;
    
    /**
     * 访问密钥密码
     */
    private String accessKeySecret;
    
    /**
     * Bucket 名称
     */
    private String bucketName;
}
