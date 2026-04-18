package com.kuaiyukuaikuai.kuaiyutravel.controller;

import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    @Autowired
    private AliOssUtil aliOssUtil;

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 使用阿里云 OSS 上传文件
            String ossUrl = aliOssUtil.upload(image);
            // 返回 OSS URL
            return Result.ok(ossUrl);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }
}