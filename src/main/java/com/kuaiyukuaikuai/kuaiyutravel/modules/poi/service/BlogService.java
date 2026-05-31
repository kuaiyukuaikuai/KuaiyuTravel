package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service;

import com.kuaiyukuaikuai.kuaiyutravel.common.utils.ScrollResult;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.vo.UserDTO;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 博客服务接口
 */
public interface BlogService extends IService<Blog> {

    /**
     * 查询热门博客
     *
     * @param current 当前页码
     * @return 热门博客列表
     */
    List<Blog> queryHotBlog(Integer current);

    /**
     * 根据ID查询博客
     *
     * @param id 博客ID
     * @return 博客详情
     */
    Blog queryBlogById(Long id);

    /**
     * 点赞博客
     *
     * @param id 博客ID
     */
    void likeBlog(Long id);

    /**
     * 查询博客点赞用户
     *
     * @param id 博客ID
     * @return 点赞用户列表
     */
    List<UserDTO> queryBlogLikes(Long id);

    /**
     * 保存博客
     *
     * @param blog 博客信息
     */
    void saveBlog(Blog blog);

    /**
     * 查询关注用户的博客
     *
     * @param max    最大ID
     * @param offset 偏移量
     * @return 博客列表
     */
    ScrollResult queryBlogOfFollow(Long max, Integer offset);

    /**
     * 根据地点id查询博客
     *
     * @param poiId   地点id
     * @param current 当前页码
     * @return 博客列表
     */
    List<Blog> queryBlogByPoiId(Integer current, Long poiId);

    /**
     * 检查是否有新动态（红点）
     * @return true表示有红点，false表示没有
     */
    Boolean checkRedDot();

    /**
     * 清除新动态红点（更新最后阅读时间）
     */
    void clearRedDot();
}
