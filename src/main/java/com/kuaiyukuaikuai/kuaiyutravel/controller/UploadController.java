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

/*    @PostMapping("blog")
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

    @PostMapping("icon")
    public Result uploadIcon(@RequestParam("file") MultipartFile image) {
        try {
            // 使用阿里云 OSS 上传文件
            String ossUrl = aliOssUtil.upload(image);
            // 返回 OSS URL
            return Result.ok(ossUrl);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }*/
    /**
     * 统一上传接口
     * 请求路径示例：
     * POST /upload/blogs  -> 传到 blogs 文件夹
     * POST /upload/icons  -> 传到 icons 文件夹
     */
    @PostMapping("/{dir}")
    public Result uploadFile(@RequestParam("file") MultipartFile file,
                             @PathVariable("dir") String dir) {
        // 1. 安全校验：防止黑客乱填路径参数（白名单机制）
        if (!"blogs".equals(dir) && !"icons".equals(dir)) {
            return Result.fail("非法的上传目录");
        }

        try {
            // 2. 将路径参数 dir 直接作为文件夹名传给工具类
            String ossUrl = aliOssUtil.upload(file, dir);
            return Result.ok(ossUrl);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }
}