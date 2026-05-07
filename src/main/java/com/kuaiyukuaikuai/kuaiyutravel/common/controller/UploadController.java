package com.kuaiyukuaikuai.kuaiyutravel.common.controller;

import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传控制器
 * 处理文件上传相关的请求
 * 
 * @author 0
 * @since 2026-04-17
 */
@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    @Autowired
    private AliOssUtil aliOssUtil;

    /**
     * 统一上传接口
     * 请求路径示例：
     * POST /upload/blogs  -> 传到 blogs 文件夹
     * POST /upload/icons  -> 传到 icons 文件夹
     * 
     * @param file 上传的文件
     * @param dir 上传目录
     * @return 上传结果
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