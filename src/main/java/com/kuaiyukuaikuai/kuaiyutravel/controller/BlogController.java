package com.kuaiyukuaikuai.kuaiyutravel.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kuaiyukuaikuai.kuaiyutravel.annotation.RateLimit;
import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.dto.UserDTO;
import com.kuaiyukuaikuai.kuaiyutravel.entity.Blog;
import com.kuaiyukuaikuai.kuaiyutravel.service.BlogService;
import com.kuaiyukuaikuai.kuaiyutravel.utils.SystemConstants;
import com.kuaiyukuaikuai.kuaiyutravel.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 博客控制器
 * 处理博客相关的请求
 * 
 * @author 0
 * @since 2026-04-17
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private BlogService blogService;

    /**
     * 保存博客
     * 
     * @param blog 博客信息
     * @return 保存结果
     */
    @RateLimit(time = 60, msg = "发布博客太快啦，请60秒后再试", prefix = "blog:save:")
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 点赞博客
     * 
     * @param id 博客id
     * @return 点赞结果
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 查询我的博客
     * 
     * @param current 当前页码
     * @return 博客列表
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 查询热门博客
     * 
     * @param current 当前页码
     * @return 热门博客列表
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 根据id查询博客
     * 
     * @param id 博客id
     * @return 博客详情
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 查询博客点赞列表
     * 
     * @param id 博客id
     * @return 点赞用户列表
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

    /**
     * 根据用户id查询博客
     * 
     * @param current 当前页码
     * @param id 用户id
     * @return 博客列表
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 查询关注的用户博客
     * 
     * @param max 最大id
     * @param offset 偏移量
     * @return 博客列表
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max, @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.queryBlogOfFollow(max, offset);
    }

    /**
     * 根据地点id查询博客
     *
     * @param poiId 地点id
     * @param current 当前页码
     * @return 博客列表
     */
    @GetMapping("/of/poi")
    public Result queryBlogByPoiId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("poiId") Long poiId) {
        return blogService.queryBlogByPoiId(current, poiId);
    }
}