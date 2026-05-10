package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.GroupMember;

/**
 * 组团成员业务接口
 * 所有方法统一使用 groupNo（String类型）作为唯一标识
 */
public interface GroupMemberService extends IService<GroupMember> {

    /**
     * 加入组团 (通过邀请码，含并发安全校验)
     */
    Result joinGroup(String groupNo);

    /**
     * 查询某个组团的所有成员列表（通过团号）
     */
    Result getMembersByGroupNo(String groupNo);

    /**
     * 查询当前用户加入过的所有组团列表
     */
    Result getMyJoinedGroups();

    /**
     * 团长踢人（通过团号）
     */
    Result removeMemberByNo(String groupNo, Long memberUserId);

    /**
     * 用户主动退出组团（通过团号）
     */
    Result exitGroupByNo(String groupNo);

    // ==================== 以下为向后兼容的旧接口（基于ID） ====================

    /**
     * 查询某个组团的所有成员列表（通过ID）
     * 
     * @deprecated 建议使用 getMembersByGroupNo(String groupNo)
     */
    Result getMembersByGroupId(Long groupId);

    /**
     * 团长踢人（通过ID）
     * 
     * @deprecated 建议使用 removeMemberByNo(String groupNo, Long memberUserId)
     */
    Result removeMember(Long groupId, Long memberUserId);

    /**
     * 用户主动退出组团（通过ID）
     * 
     * @deprecated 建议使用 exitGroupByNo(String groupNo)
     */
    Result exitGroup(Long groupId);
}
