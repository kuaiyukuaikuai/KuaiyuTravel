package com.kuaiyukuaikuai.kuaiyutravel.common.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import com.kuaiyukuaikuai.kuaiyutravel.common.properties.AliOssProperties;
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

    /** 单例 OSS 客户端，避免每次上传都新建连接 */
    private volatile OSS ossClient;

    /**
     * 获取（延迟初始化）单例 OSS 客户端
     */
    private OSS getOssClient() {
        if (ossClient == null) {
            synchronized (this) {
                if (ossClient == null) {
                    ossClient = new OSSClientBuilder().build(
                            aliOssProperties.getEndpoint(),
                            aliOssProperties.getAccessKeyId(),
                            aliOssProperties.getAccessKeySecret()
                    );
                }
            }
        }
        return ossClient;
    }

    /**
     * 上传图片到阿里云 OSS
     *
     * @param file 上传的文件
     * @return OSS 上的外网访问 URL
     */
    public String upload(MultipartFile file, String dirName) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名不能为空");
        }

        // 提取文件后缀，防止无后缀文件导致 StringIndexOutOfBoundsException
        int dotIndex = originalFilename.lastIndexOf(".");
        if (dotIndex == -1 || dotIndex == originalFilename.length() - 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件缺少有效后缀");
        }
        String suffix = originalFilename.substring(dotIndex);

        // 生成唯一文件名（UUID + 后缀）
        String uuid = UUID.randomUUID().toString();

        // 获取当前日期并格式化为 yyyy/MM/dd
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        // 构建完整路径：目录名/年/月/日/uuid.后缀
        String filePath = dirName + "/" + datePath + "/" + uuid + suffix;

        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    aliOssProperties.getBucketName(),
                    filePath,
                    inputStream
            );
            getOssClient().putObject(putObjectRequest);

            String url = "https://" + aliOssProperties.getBucketName() + "."
                    + aliOssProperties.getEndpoint() + "/" + filePath;

            log.debug("文件上传成功，OSS URL: {}", url);
            return url;

        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "文件上传失败: " + e.getMessage());
        }
    }
}
