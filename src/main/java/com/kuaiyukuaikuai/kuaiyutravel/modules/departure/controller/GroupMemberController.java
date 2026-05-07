package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.controller;

import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupMemberService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 组团成员管理控制器
 * 处理加入、退出、踢人、查询成员等社交关系
 */
@RestController
@RequestMapping("/group-member")
public class GroupMemberController {

    @Resource
    private GroupMemberService groupMemberService;

    /**
     * 【加入】用户申请加入某个组团
     * POST /group-member/join/123
     */
    @PostMapping("/join/{groupId}")
    public Result joinGroup(@PathVariable("groupId") Long groupId) {
        return groupMemberService.joinGroup(groupId);
    }

    /**
     * 【查询成员】查看某个组团里有哪些人
     * GET /group-member/list/123
     */
    @GetMapping("/list/{groupId}")
    public Result getMembersByGroupId(@PathVariable("groupId") Long groupId) {
        return groupMemberService.getMembersByGroupId(groupId);
    }

    /**
     * 【我的组团】查询当前登录用户参加的所有组团
     * GET /group-member/my
     */
    @GetMapping("/my")
    public Result getMyJoinedGroups() {
        return groupMemberService.getMyJoinedGroups();
    }

    /**
     * 【踢人】团长移除某个成员
     * DELETE /group-member/kick/123/456
     */
    @DeleteMapping("/kick/{groupId}/{userId}")
    public Result removeMember(@PathVariable("groupId") Long groupId, 
                               @PathVariable("userId") Long userId) {
        return groupMemberService.removeMember(groupId, userId);
    }

    /**
     * 【退出】普通成员主动退出组团
     * DELETE /group-member/exit/123
     */
    @DeleteMapping("/exit/{groupId}")
    public Result exitGroup(@PathVariable("groupId") Long groupId) {
        return groupMemberService.exitGroup(groupId);
    }
}