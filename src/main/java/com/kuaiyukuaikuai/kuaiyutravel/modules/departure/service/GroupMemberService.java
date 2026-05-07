package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.GroupMember;

/**
 * 组团成员业务接口
 */
public interface GroupMemberService extends IService<GroupMember> {

    /**
     * 加入组团 (含并发安全校验)
     */
    Result joinGroup(Long groupId);

    /**
     * 查询某个组团的所有成员列表
     */
    Result getMembersByGroupId(Long groupId);

    /**
     * 查询当前用户加入过的所有组团列表
     */
    Result getMyJoinedGroups();

    /**
     * 团长踢人
     */
    Result removeMember(Long groupId, Long memberUserId);

    /**
     * 用户主动退出组团
     */
    Result exitGroup(Long groupId);
}