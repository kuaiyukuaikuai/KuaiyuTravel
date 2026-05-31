package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.GroupMember;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.enums.GroupStatus;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.enums.MemberRole;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.mapper.GroupMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.mapper.GroupMemberMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupMemberService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.vo.GroupMemberVO;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.vo.UserDTO;


import com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity.User;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组团成员业务实现类
 * 统一使用 groupNo（String类型）作为唯一标识，避免前后端 Long 精度丢失问题
 */
@Service
public class GroupMemberServiceImpl extends ServiceImpl<GroupMemberMapper, GroupMember> implements GroupMemberService {

    @Resource
    private GroupMapper groupMapper;

    @Resource
    private UserService userService;

    /**
     * 通过团号查询组团，不存在则抛出异常
     */
    private Group getGroupByGroupNoOrThrow(String groupNo) {
        if (groupNo == null || groupNo.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "组团编号不能为空");
        }
        Group group = groupMapper.getGroupByGroupNo(groupNo);
        if (group == null) {
            throw new BusinessException(ErrorCode.GROUP_NOT_FOUND, "未找到该组团信息");
        }
        return group;
    }

    /**
     * 校验当前用户是否为团长
     */
    private void validateLeaderPermission(Group group, Long currentUserId) {
        if (!group.getLeaderId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.GROUP_NO_PERMISSION, "只有团长可以执行此操作");
        }
    }

    /**
     * 校验不能对自己执行踢出操作
     */
    private void validateNotSelfRemove(Long memberUserId, Long currentUserId) {
        if (memberUserId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.GROUP_LEADER_CANNOT_REMOVE_SELF, "团长不能踢出自己，如需解散请使用解散功能");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void joinGroup(String groupNo) {
        Long userId = UserHolder.getUserId();

        Group group = getGroupByGroupNoOrThrow(groupNo);
        Long groupId = group.getId();

        // 校验组团状态：只有招募中的团才能加入
        if (group.getStatus() != GroupStatus.RECRUITING.getCode()) {
            throw new BusinessException(ErrorCode.GROUP_CLOSED, "该组团已关闭或已结束");
        }

        // 3. 校验是否已经加入过该团
        Long count = lambdaQuery()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId)
                .count();
        if (count > 0) {
            throw new BusinessException(ErrorCode.GROUP_ALREADY_JOINED, "你已经是该团成员，请勿重复加入");
        }

        // 4. 并发安全防超卖：原子操作更新人数
        int updateCount = groupMapper.incrementCurrentPeople(groupId);
        if (updateCount == 0) {
            throw new BusinessException(ErrorCode.GROUP_FULL, "加入失败，组团人数已满或该团已关闭");
        }

        // 5. 插入成员记录
        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(MemberRole.MEMBER.getCode());
        save(member);
    }

    /**
     * 【核心方法：通过团号查询成员列表】
     * 前端传递 String 类型的 groupNo，避免 JavaScript Long 精度丢失问题
     */
    @Override
    public List<GroupMemberVO> getMembersByGroupNo(String groupNo) {
        Group group = getGroupByGroupNoOrThrow(groupNo);

        // 3. 获取到精确的 groupId（Long 类型），调用原有的查询逻辑
        Long groupId = group.getId();
        return getMembersByGroupId(groupId);
    }

    /**
     * 【核心重构：引入 VO 和 DTO】
     * 查询某个组团的所有成员列表（带用户头像和昵称）
     */
    @Override
    public List<GroupMemberVO> getMembersByGroupId(Long groupId) {
        // 1. 查出该团所有的成员关联关系
        List<GroupMember> memberList = lambdaQuery()
                .eq(GroupMember::getGroupId, groupId)
                .list();

        if (memberList.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 提取所有的 userId
        List<Long> userIds = memberList.stream()
                .map(GroupMember::getUserId)
                .collect(Collectors.toList());

        // 3. 批量查询用户信息 (MyBatis-Plus 的内置方法)
        List<User> users = userService.listByIds(userIds);

        // 4. 将用户信息转换为 UserDTO 的 Map，方便快速通过 ID 获取，降低时间复杂度到 O(1)
        Map<Long, UserDTO> userDtoMap = users.stream().collect(Collectors.toMap(
                User::getId,
                user -> {
                    UserDTO dto = new UserDTO();
                    dto.setId(user.getId());
                    dto.setNickName(user.getNickName());
                    dto.setIcon(user.getIcon());
                    return dto;
                }
        ));

        // 5. 遍历组团成员表，组装最终返回前端的 GroupMemberVO 集合
        List<GroupMemberVO> voList = memberList.stream().map(member -> {
            GroupMemberVO vo = new GroupMemberVO();
            vo.setRole(member.getRole());
            vo.setJoinTime(member.getJoinTime());

            // 从 map 中获取该成员对应的用户信息，并赋值到继承自 UserDTO 的属性上
            UserDTO userDto = userDtoMap.get(member.getUserId());
            if (userDto != null) {
                vo.setId(userDto.getId());
                vo.setNickName(userDto.getNickName());
                vo.setIcon(userDto.getIcon());
            }
            return vo;
        }).collect(Collectors.toList());

        // 6. 返回组装好的 VO 数据给前端
        return voList;
    }

    @Override
    public List<Group> getMyJoinedGroups() {
        Long userId = UserHolder.getUserId();

        List<GroupMember> myMemberships = lambdaQuery()
                .eq(GroupMember::getUserId, userId)
                .list();

        if (myMemberships.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> groupIds = myMemberships.stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toList());

        return groupMapper.selectBatchIds(groupIds);
    }

    /**
     * 【核心方法：通过团号踢人】
     * 前端传递 String 类型的 groupNo，避免 JavaScript Long 精度丢失问题
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMemberByNo(String groupNo, Long memberUserId) {
        Group group = getGroupByGroupNoOrThrow(groupNo);
        Long currentUserId = UserHolder.getUserId();

        validateLeaderPermission(group, currentUserId);
        validateNotSelfRemove(memberUserId, currentUserId);

        Long groupId = group.getId();

        // 6. 删除成员记录
        boolean removed = lambdaUpdate()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, memberUserId)
                .remove();

        // 7. 如果删除成功，减少当前人数
        if (removed) {
            groupMapper.decrementCurrentPeople(groupId);
        }
    }

    /**
     * 【通过团号退出组团】
     * 前端传递 String 类型的 groupNo，避免 JavaScript Long 精度丢失问题
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exitGroupByNo(String groupNo) {
        Group group = getGroupByGroupNoOrThrow(groupNo);
        Long groupId = group.getId();
        exitGroup(groupId);
    }

    // ==================== 以下为向后兼容的旧接口实现（基于ID） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long groupId, Long memberUserId) {
        Long currentUserId = UserHolder.getUserId();

        Group group = groupMapper.selectById(groupId);
        if (group == null || !group.getLeaderId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.GROUP_NO_PERMISSION, "只有团长可以踢出成员");
        }

        if (memberUserId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.GROUP_LEADER_CANNOT_REMOVE_SELF, "团长不能踢出自己，如需解散请使用解散功能");
        }

        boolean removed = lambdaUpdate()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, memberUserId)
                .remove();

        if (removed) {
            groupMapper.decrementCurrentPeople(groupId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exitGroup(Long groupId) {
        Long userId = UserHolder.getUserId();
        Group group = groupMapper.selectById(groupId);

        if (group != null && group.getLeaderId().equals(userId)) {
            throw new BusinessException(ErrorCode.GROUP_LEADER_CANNOT_EXIT, "你是团长，如需退出请直接解散组团");
        }

        boolean removed = lambdaUpdate()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId)
                .remove();

        if (removed) {
            groupMapper.decrementCurrentPeople(groupId);
        }
    }
}
