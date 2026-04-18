package com.kuaiyukuaikuai.kuaiyutravel.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.kuaiyukuaikuai.kuaiyutravel.properties.AliOssProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 阿里云 OSS 上传工具类
 */
@Slf4j
@Component
public class AliOssUtil {

    @Autowired
    private AliOssProperties aliOssProperties;

    /**
     * 上传图片到阿里云 OSS
     *
     * @param file 上传的文件
     * @return OSS 上的外网访问 URL
     */
    public String upload(MultipartFile file) {
        try {
            // 获取原始文件名和后缀
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                throw new RuntimeException("文件名不能为空");
            }
            
            // 提取文件后缀
            String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            
            // 生成唯一文件名（UUID + 后缀）
            String uuid = UUID.randomUUID().toString();
            
            // 获取当前日期并格式化为 yyyy/MM/dd
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            
            // 构建完整路径：blogs/年/月/日/uuid.后缀
            String filePath = "blogs/" + datePath + "/" + uuid + suffix;
            
            // 创建 OSS 客户端
            OSS ossClient = new OSSClientBuilder().build(
                    aliOssProperties.getEndpoint(),
                    aliOssProperties.getAccessKeyId(),
                    aliOssProperties.getAccessKeySecret()
            );
            
            try {
                // 获取文件输入流
                InputStream inputStream = file.getInputStream();
                
                // 上传文件到 OSS
                PutObjectRequest putObjectRequest = new PutObjectRequest(
                        aliOssProperties.getBucketName(),
                        filePath,
                        inputStream
                );
                ossClient.putObject(putObjectRequest);
                
                // 拼接并返回外网访问 URL
                String url = "https://" + aliOssProperties.getBucketName() + "." 
                        + aliOssProperties.getEndpoint() + "/" + filePath;
                
                log.debug("文件上传成功，OSS URL: {}", url);
                return url;
                
            } finally {
                // 关闭 OSS 客户端
                ossClient.shutdown();
            }
            
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }
}
