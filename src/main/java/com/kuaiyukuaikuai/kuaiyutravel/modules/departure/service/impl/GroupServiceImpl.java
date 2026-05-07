package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.GroupMember;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.mapper.GroupMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupMemberService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupService;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, Group> implements GroupService {

    @Resource
    private GroupMemberService groupMemberService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createGroup(Group group) {
        if (group.getTitle() == null || group.getTitle().trim().isEmpty()) {
            return Result.fail("组团标题不能为空");
        }
        if (group.getMaxPeople() == null || group.getMaxPeople() <= 0) {
            return Result.fail("人数上限必须大于0");
        }

        Long userId = UserHolder.getUser().getId();
        group.setLeaderId(userId);

        String dateStr = DateUtil.format(LocalDateTime.now(), "yyyyMMdd");
        String randomStr = RandomUtil.randomString(4).toUpperCase();
        group.setGroupNo("KY-" + dateStr + "-" + randomStr);

        group.setCurrentPeople(1);
        group.setStatus(0);

        this.save(group);

        GroupMember member = new GroupMember();
        member.setGroupId(group.getId());
        member.setUserId(userId);
        member.setRole(0);
        groupMemberService.save(member);

        return Result.ok(group.getGroupNo());
    }

    @Override
    public Result queryByGroupNo(String groupNo) {
        if (groupNo == null || groupNo.trim().isEmpty()) {
            return Result.fail("组团编号不能为空");
        }

        Group group = lambdaQuery()
                .eq(Group::getGroupNo, groupNo)
                .one();

        if (group == null) {
            return Result.fail("未找到该组团信息");
        }
        return Result.ok(group);
    }

    @Override
    public Result updateGroup(Group group) {
        if (group.getId() == null) {
            return Result.fail("组团ID不能为空");
        }

        Group oldGroup = getById(group.getId());
        if (oldGroup == null) {
            return Result.fail("组团不存在");
        }

        Long currentUserId = UserHolder.getUser().getId();
        if (!oldGroup.getLeaderId().equals(currentUserId)) {
            return Result.fail("只有团长可以修改组团信息！");
        }

        group.setGroupNo(null);
        group.setLeaderId(null);
        group.setCurrentPeople(null);
        group.setStatus(null);

        updateById(group);
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteGroup(Long groupId) {
        if (groupId == null) {
            return Result.fail("组团ID不能为空");
        }

        Group group = getById(groupId);
        if (group == null) {
            return Result.ok();
        }

        Long currentUserId = UserHolder.getUser().getId();
        if (!group.getLeaderId().equals(currentUserId)) {
            return Result.fail("只有团长可以解散组团！");
        }

        removeById(groupId);
        groupMemberService.lambdaUpdate()
                .eq(GroupMember::getGroupId, groupId)
                .remove();

        return Result.ok();
    }

    /**
     * 【新增加：分页查询逻辑】
     */
    @Override
    public Result queryGroupPage(Integer current, Integer size) {
        // 1. 设置默认分页参数
        if (current == null) current = 1;
        if (size == null) size = 10;

        // 2. 构建分页对象
        Page<Group> page = new Page<>(current, size);

        // 3. 执行分页查询：只查招募中的团 (status = 0)，按创建时间倒序排列
        lambdaQuery()
                .eq(Group::getStatus, 0)
                .orderByDesc(Group::getCreateTime)
                .page(page);

        // 4. 返回分页后的记录列表
        return Result.ok(page.getRecords());
    }
}