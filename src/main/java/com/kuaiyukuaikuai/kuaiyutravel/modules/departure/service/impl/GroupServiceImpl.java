package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.SystemConstants;
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

/**
 * 组团业务实现类
 */
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

        // 防篡改保护
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
     * 分页查询所有招募中的组团信息
     */
    @Override
    public Result queryGroupPage(Integer current, Integer size) {
        // 1. 分页参数健壮性处理
        if (current == null || current < 1) {
            current = 1;
        }
        if (size == null || size <= 0) {
            // 复用全局常量
            size = SystemConstants.DEFAULT_PAGE_SIZE;
        } else if (size > SystemConstants.MAX_PAGE_SIZE) {
            // 防止单次查询量过大
            size = SystemConstants.MAX_PAGE_SIZE;
        }

        // 2. 构造 MyBatis-Plus 分页对象
        Page<Group> page = new Page<>(current, size);

        // 3. 执行条件查询并分页
        lambdaQuery()
                .eq(Group::getStatus, 0) // 只查询招募中的
                .orderByDesc(Group::getCreateTime) // 按发布时间倒序
                .page(page);

        // 4. 返回完整的分页元数据对象，包含 total, pages, records 等
        return Result.ok(page);
    }
}