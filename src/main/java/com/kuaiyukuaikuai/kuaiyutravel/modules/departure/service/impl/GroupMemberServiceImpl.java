package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.GroupMember;
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
 */
@Service
public class GroupMemberServiceImpl extends ServiceImpl<GroupMemberMapper, GroupMember> implements GroupMemberService {

    @Resource
    private GroupMapper groupMapper;

    @Resource
    private UserService userService; // 注入用户模块的服务，用于获取昵称和头像

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result joinGroup(String groupNo) {
        Long userId = UserHolder.getUser().getId();

        // 1. 通过邀请码查询组团信息，同时获取真实的 groupId（Long 类型）
        Group group = groupMapper.getGroupByGroupNo(groupNo);

/*        Group group = lambdaQuery(Group.class)
                .eq(Group::getGroupNo, groupNo)
                .one();*/

        if (group == null) {
            return Result.fail("未找到该组团，请检查邀请码是否正确");
        }

        Long groupId = group.getId();

        // 2. 校验组团状态：只有招募中的团才能加入
        if (group.getStatus() != 0) {
            return Result.fail("该组团已关闭或已结束");
        }

        // 3. 校验是否已经加入过该团
        Long count = lambdaQuery()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId)
                .count();
        if (count > 0) {
            return Result.fail("你已经是该团成员，请勿重复加入");
        }

        // 4. 并发安全防超卖：原子操作更新人数
        int updateCount = groupMapper.incrementCurrentPeople(groupId);
        if (updateCount == 0) {
            return Result.fail("加入失败，组团人数已满或该团已关闭");
        }

        // 5. 插入成员记录
        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(1); // 1-普通成员
        save(member);

        return Result.ok();
    }
    /**
     * 【核心重构：引入 VO 和 DTO】
     * 查询某个组团的所有成员列表（带用户头像和昵称）
     */
    @Override
    public Result getMembersByGroupId(Long groupId) {
        // 1. 查出该团所有的成员关联关系
        List<GroupMember> memberList = lambdaQuery()
                .eq(GroupMember::getGroupId, groupId)
                .list();

        if (memberList.isEmpty()) {
            return Result.ok(Collections.emptyList());
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
        return Result.ok(voList);
    }

    @Override
    public Result getMyJoinedGroups() {
        Long userId = UserHolder.getUser().getId();

        List<GroupMember> myMemberships = lambdaQuery()
                .eq(GroupMember::getUserId, userId)
                .list();

        if (myMemberships.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> groupIds = myMemberships.stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toList());

        List<Group> groupList = groupMapper.selectBatchIds(groupIds);
        return Result.ok(groupList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result removeMember(Long groupId, Long memberUserId) {
        Long currentUserId = UserHolder.getUser().getId();

        Group group = groupMapper.selectById(groupId);
        if (group == null || !group.getLeaderId().equals(currentUserId)) {
            return Result.fail("只有团长可以踢出成员");
        }

        if (memberUserId.equals(currentUserId)) {
            return Result.fail("团长不能踢出自己，如需解散请使用解散功能");
        }

        boolean removed = lambdaUpdate()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, memberUserId)
                .remove();

        if (removed) {
            groupMapper.decrementCurrentPeople(groupId);
        }

        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result exitGroup(Long groupId) {
        Long userId = UserHolder.getUser().getId();
        Group group = groupMapper.selectById(groupId);

        if (group != null && group.getLeaderId().equals(userId)) {
            return Result.fail("你是团长，如需退出请直接解散组团");
        }

        boolean removed = lambdaUpdate()
                .eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId)
                .remove();

        if (removed) {
            groupMapper.decrementCurrentPeople(groupId);
        }

        return Result.ok();
    }
}