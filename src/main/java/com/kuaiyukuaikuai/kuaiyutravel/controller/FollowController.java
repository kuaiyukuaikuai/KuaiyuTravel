package com.kuaiyukuaikuai.kuaiyutravel.controller;

import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.service.FollowService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 关注控制器
 * 处理用户关注相关的请求
 * 
 * @author 0
 * @since 2026-04-17
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private FollowService followService;

    /**
     * 关注或取消关注用户
     * 
     * @param followUserId 被关注用户id
     * @param isFollow 是否关注
     * @return 操作结果
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 判断是否关注用户
     * 
     * @param followUserId 被关注用户id
     * @return 是否关注
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    /**
     * 查询共同关注
     * 
     * @param id 用户id
     * @return 共同关注用户列表
     */
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id) {
        return followService.followCommons(id);
    }

    /**
     * 查询粉丝列表
     * 
     * @param id 用户id
     * @param current 当前页码
     * @return 粉丝列表
     */
    @GetMapping("/fans/{id}")
    public Result queryFans(@PathVariable("id") Long id,
                           @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return followService.queryFans(id, current);
    }

    /**
     * 查询关注列表
     * 
     * @param id 用户id
     * @param current 当前页码
     * @return 关注列表
     */
    @GetMapping("/followings/{id}")
    public Result queryFollowings(@PathVariable("id") Long id,
                                  @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return followService.queryFollowings(id, current);
    }
}