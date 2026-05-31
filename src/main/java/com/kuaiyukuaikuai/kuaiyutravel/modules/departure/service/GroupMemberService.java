package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.GroupMember;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.vo.GroupMemberVO;

import java.util.List;

/**
 * 组团成员业务接口
 * 所有方法统一使用 groupNo（String类型）作为唯一标识
 */
public interface GroupMemberService extends IService<GroupMember> {

    /**
     * 加入组团 (通过邀请码，含并发安全校验)
     */
    void joinGroup(String groupNo);

    /**
     * 查询某个组团的所有成员列表（通过团号）
     *
     * @return 成员列表
     */
    List<GroupMemberVO> getMembersByGroupNo(String groupNo);

    /**
     * 查询当前用户加入过的所有组团列表
     *
     * @return 组团列表
     */
    List<Group> getMyJoinedGroups();

    /**
     * 团长踢人（通过团号）
     */
    void removeMemberByNo(String groupNo, Long memberUserId);

    /**
     * 用户主动退出组团（通过团号）
     */
    void exitGroupByNo(String groupNo);

    // ==================== 以下为向后兼容的旧接口（基于ID） ====================

    /**
     * 查询某个组团的所有成员列表（通过ID）
     *
     * @return 成员列表
     * @deprecated 建议使用 getMembersByGroupNo(String groupNo)
     */
    List<GroupMemberVO> getMembersByGroupId(Long groupId);

    /**
     * 团长踢人（通过ID）
     *
     * @deprecated 建议使用 removeMemberByNo(String groupNo, Long memberUserId)
     */
    void removeMember(Long groupId, Long memberUserId);

    /**
     * 用户主动退出组团（通过ID）
     *
     * @deprecated 建议使用 exitGroupByNo(String groupNo)
     */
    void exitGroup(Long groupId);
}
