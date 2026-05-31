package com.kuaiyukuaikuai.kuaiyutravel.modules.my.controller;

import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.FollowService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.vo.UserDTO;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

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
        followService.follow(followUserId, isFollow);
        return Result.ok();
    }

    /**
     * 判断是否关注用户
     *
     * @param followUserId 被关注用户id
     * @return 是否关注
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        Boolean isFollow = followService.isFollow(followUserId);
        return Result.ok(isFollow);
    }

    /**
     * 查询共同关注
     *
     * @param id 用户id
     * @return 共同关注用户列表
     */
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id) {
        List<UserDTO> users = followService.followCommons(id);
        return Result.ok(users);
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
        List<UserDTO> fans = followService.queryFans(id, current);
        return Result.ok(fans);
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
        List<UserDTO> followings = followService.queryFollowings(id, current);
        return Result.ok(followings);
    }
}
