package com.kuaiyukuaikuai.kuaiyutravel.common.controller;

import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

    /** 允许的文件后缀白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "mp4", "mov"
    ));

    /** 最大文件大小：10MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

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
        // 1. 目录白名单校验
        if (!"blogs".equals(dir) && !"icons".equals(dir)) {
            return Result.fail("非法的上传目录");
        }

        // 2. 空文件校验
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "上传文件不能为空");
        }

        // 3. 文件大小校验
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件大小超过限制（最大10MB）");
        }

        // 4. 文件后缀白名单校验
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.lastIndexOf(".") == -1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名格式不正确");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的文件类型，仅允许: " + ALLOWED_EXTENSIONS);
        }

        // 5. MIME 类型校验（防止伪造后缀）
        String contentType = file.getContentType();
        if (contentType == null || !(contentType.startsWith("image/") || contentType.startsWith("video/"))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件类型与安全校验不匹配");
        }

        try {
            String ossUrl = aliOssUtil.upload(file, dir);
            return Result.ok(ossUrl);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "文件上传失败: " + e.getMessage());
        }
    }
}