package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service;

import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.BlogComments;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.vo.BlogCommentsVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 博客评论服务接口
 */
public interface BlogCommentsService extends IService<BlogComments> {

    /**
     * 新增评论
     * @param blogComments 评论信息
     */
    void saveComment(BlogComments blogComments);

    /**
     * 查询评论列表
     * @param blogId 博客ID
     * @param current 当前页码
     * @return 评论列表
     */
    List<BlogCommentsVO> queryCommentsByBlogId(Long blogId, Integer current);

    /**
     * 评论点赞
     * @param commentId 评论ID
     */
    void likeComment(Long commentId);
}
