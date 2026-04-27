package com.kuaiyukuaikuai.kuaiyutravel.service;

import com.kuaiyukuaikuai.kuaiyutravel.dto.BlogCommentsVO;
import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 博客评论服务接口
 */
public interface BlogCommentsService extends IService<BlogComments> {

    /**
     * 新增评论
     * @param blogComments 评论信息
     * @return 操作结果
     */
    Result saveComment(BlogComments blogComments);

    /**
     * 查询评论列表
     * @param blogId 博客ID
     * @param current 当前页码
     * @return 评论列表
     */
    Result queryCommentsByBlogId(Long blogId, Integer current);

    /**
     * 评论点赞
     * @param commentId 评论ID
     * @return 操作结果
     */
    Result likeComment(Long commentId);
}