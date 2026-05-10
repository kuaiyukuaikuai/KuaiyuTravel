package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.controller;

import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupMemberService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 组团成员管理控制器
 * 处理加入、退出、踢人、查询成员等社交关系
 * 所有接口统一使用 groupNo（String类型）作为唯一标识，避免前后端 Long 精度丢失问题。
 */
@RestController
@RequestMapping("/group-member")
public class GroupMemberController {

    @Resource
    private GroupMemberService groupMemberService;

    /**
     * 【加入】用户申请加入某个组团（通过邀请码）
     * POST /group-member/join/{groupNo}
     */
    @PostMapping("/join/{groupNo}")
    public Result joinGroup(@PathVariable("groupNo") String groupNo) {
        return groupMemberService.joinGroup(groupNo);
    }

    /**
     * 【查询成员】查看某个组团里有哪些人（通过团号，推荐使用）
     * GET /group-member/list/by-groupno/{groupNo}
     */
    @GetMapping("/list/by-groupno/{groupNo}")
    public Result getMembersByGroupNo(@PathVariable("groupNo") String groupNo) {
        return groupMemberService.getMembersByGroupNo(groupNo);
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
     * 【踢人】团长移除某个成员（通过团号）
     * DELETE /group-member/kick/by-groupno/{groupNo}/{userId}
     *
     * @param groupNo 组团编号（String 类型）
     * @param userId  被踢成员的用户ID
     */
    @DeleteMapping("/kick/by-groupno/{groupNo}/{userId}")
    public Result removeMemberByNo(
            @PathVariable("groupNo") String groupNo,
            @PathVariable("userId") Long userId) {
        return groupMemberService.removeMemberByNo(groupNo, userId);
    }

    /**
     * 【退出】普通成员主动退出组团（通过团号，推荐使用）
     * DELETE /group-member/exit/by-groupno/{groupNo}
     */
    @DeleteMapping("/exit/by-groupno/{groupNo}")
    public Result exitGroupByNo(@PathVariable("groupNo") String groupNo) {
        return groupMemberService.exitGroupByNo(groupNo);
    }

    // ==================== 以下为向后兼容的旧接口（基于ID） ====================

    /**
     * 【查询成员】查看某个组团里有哪些人（通过ID，向后兼容）
     * 
     * @deprecated 建议使用 GET /group-member/list/by-groupno/{groupNo}
     */
    @GetMapping("/list/{groupId}")
    public Result getMembersByGroupId(@PathVariable("groupId") Long groupId) {
        return groupMemberService.getMembersByGroupId(groupId);
    }

    /**
     * 【踢人】团长移除某个成员（通过ID，向后兼容）
     * 
     * @deprecated 建议使用 DELETE /group-member/kick/by-groupno/{groupNo}/{userId}
     */
    @DeleteMapping("/kick/{groupId}/{userId}")
    public Result removeMember(@PathVariable("groupId") Long groupId,
            @PathVariable("userId") Long userId) {
        return groupMemberService.removeMember(groupId, userId);
    }

    /**
     * 【退出】普通成员主动退出组团（通过ID，向后兼容）
     * 
     * @deprecated 建议使用 DELETE /group-member/exit/by-groupno/{groupNo}
     */
    @DeleteMapping("/exit/{groupId}")
    public Result exitGroup(@PathVariable("groupId") Long groupId) {
        return groupMemberService.exitGroup(groupId);
    }
}
