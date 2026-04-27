package com.kuaiyukuaikuai.kuaiyutravel.controller;

import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.entity.BlogComments;
import com.kuaiyukuaikuai.kuaiyutravel.service.BlogCommentsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 博客评论控制器
 * 处理博客评论相关的请求
 * 
 * @author 0
 * @since 2026-04-17
 */
@Slf4j
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private BlogCommentsService blogCommentsService;

    /**
     * 新增评论
     * @param comment 评论信息
     * @return 操作结果
     */
    @PostMapping
    public Result saveComment(@RequestBody BlogComments comment) {
        return blogCommentsService.saveComment(comment);
    }

    /**
     * 查询评论列表
     * @param blogId 博客ID
     * @param current 当前页码
     * @return 评论列表
     */
    @GetMapping
    public Result queryComments(@RequestParam Long blogId, @RequestParam(defaultValue = "1") Integer current) {
        return blogCommentsService.queryCommentsByBlogId(blogId, current);
    }

    /**
     * 评论点赞
     * @param id 评论ID
     * @return 操作结果
     */
    @PutMapping("/like/{id}")
    public Result likeComment(@PathVariable Long id) {
        return blogCommentsService.likeComment(id);
    }
}