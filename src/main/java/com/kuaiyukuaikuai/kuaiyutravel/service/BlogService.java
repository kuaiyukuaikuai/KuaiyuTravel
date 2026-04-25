package com.kuaiyukuaikuai.kuaiyutravel.service;

import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 博客服务接口
 */
public interface BlogService extends IService<Blog> {

    /**
     * 查询热门博客
     * @param current 当前页码
     * @return 热门博客列表
     */
    Result queryHotBlog(Integer current);

    /**
     * 根据ID查询博客
     * @param id 博客ID
     * @return 博客详情
     */
    Result queryBlogById(Long id);

    /**
     * 点赞博客
     * @param id 博客ID
     * @return 操作结果
     */
    Result likeBlog(Long id);

    /**
     * 查询博客点赞用户
     * @param id 博客ID
     * @return 点赞用户列表
     */
    Result queryBlogLikes(Long id);

    /**
     * 保存博客
     * @param blog 博客信息
     * @return 保存结果
     */
    Result saveBlog(Blog blog);

    /**
     * 查询关注用户的博客
     * @param max 最大ID
     * @param offset 偏移量
     * @return 博客列表
     */
    Result queryBlogOfFollow(Long max, Integer offset);

    /**
     * 根据地点id查询博客
     * @param poiId 地点id
     * @param current 当前页码
     * @return 博客列表
     */
    Result queryBlogByPoiId(Integer current, Long poiId);
}