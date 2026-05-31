package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.SystemConstants;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.GroupMember;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.enums.GroupStatus;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.enums.MemberRole;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.mapper.GroupMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupMemberService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupService;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 组团业务实现类
 * 统一使用 groupNo（String类型）作为唯一标识，避免前后端 Long 精度丢失问题
 */
@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, Group> implements GroupService {

    @Resource
    private GroupMemberService groupMemberService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createGroup(Group group) {
        // 1. 参数校验（事务外前置校验）
        if (group.getTitle() == null || group.getTitle().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "组团标题不能为空");
        }
        if (group.getMaxPeople() == null || group.getMaxPeople() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "人数上限必须大于0");
        }

        Long userId = UserHolder.getUserId();
        group.setLeaderId(userId);

        // 2. 生成团号（事务外）
        String dateStr = DateUtil.format(LocalDateTime.now(), "yyyyMMdd");
        String randomStr = RandomUtil.randomString(4).toUpperCase();
        group.setGroupNo("KY-" + dateStr + "-" + randomStr);

        group.setCurrentPeople(1);
        group.setStatus(GroupStatus.RECRUITING.getCode());

        // 3. 数据库操作（受 Spring AOP 事务代理保护）
        this.save(group);

        GroupMember member = new GroupMember();
        member.setGroupId(group.getId());
        member.setUserId(userId);
        member.setRole(MemberRole.LEADER.getCode());
        groupMemberService.save(member);

        return group.getGroupNo();
    }

    @Override
    public Group queryByGroupNo(String groupNo) {
        if (groupNo == null || groupNo.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "组团编号不能为空");
        }

        Group group = lambdaQuery()
                .eq(Group::getGroupNo, groupNo)
                .one();

        if (group == null) {
            throw new BusinessException(ErrorCode.GROUP_NOT_FOUND, "未找到该组团信息");
        }
        return group;
    }

    @Override
    public void updateGroup(Group group) {
        // 优先使用 groupNo 查询，如果 groupNo 为空则使用 id（向后兼容）
        Group oldGroup;

        if (group.getGroupNo() != null && !group.getGroupNo().trim().isEmpty()) {
            // 推荐方式：通过 groupNo 查询
            oldGroup = lambdaQuery()
                    .eq(Group::getGroupNo, group.getGroupNo())
                    .one();
        } else if (group.getId() != null) {
            // 向后兼容：通过 ID 查询
            oldGroup = getById(group.getId());
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "组团ID或团号不能为空");
        }

        if (oldGroup == null) {
            throw new BusinessException(ErrorCode.GROUP_NOT_FOUND, "组团不存在");
        }

        Long currentUserId = UserHolder.getUserId();
        if (!oldGroup.getLeaderId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.GROUP_NO_PERMISSION, "只有团长可以修改组团信息！");
        }

        // 防篡改保护：不允许修改这些关键字段
        group.setGroupNo(null); // 团号不可修改
        group.setLeaderId(null); // 团长不可修改
        group.setCurrentPeople(null); // 人数由系统管理
        group.setStatus(null); // 状态由系统管理

        // 确保使用正确的 ID 进行更新
        group.setId(oldGroup.getId());
        updateById(group);
    }

    /**
     * 【核心方法：通过团号解散/删除组团】
     * 前端传递 String 类型的 groupNo，避免 JavaScript Long 精度丢失问题
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroupByNo(String groupNo) {
        // 1. 参数校验
        if (groupNo == null || groupNo.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "组团编号不能为空");
        }

        // 2. 通过 groupNo 查询组团信息，获取真实的 groupId（Long 类型）
        Group group = lambdaQuery()
                .eq(Group::getGroupNo, groupNo)
                .one();

        if (group == null) {
            return; // 不存在则视为成功（幂等性）
        }

        // 3. 校验团长权限
        Long currentUserId = UserHolder.getUserId();
        if (!group.getLeaderId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.GROUP_NO_PERMISSION, "只有团长可以解散组团！");
        }

        // 4. 获取精确的 groupId 进行删除操作
        Long groupId = group.getId();

        // 5. 删除组团记录
        removeById(groupId);

        // 6. 删除所有成员关联关系
        groupMemberService.lambdaUpdate()
                .eq(GroupMember::getGroupId, groupId)
                .remove();
    }

    /**
     * 分页查询所有招募中的组团信息
     */
    @Override
    public Page<Group> queryGroupPage(Integer current, Integer size) {
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
                .eq(Group::getStatus, GroupStatus.RECRUITING.getCode()) // 只查询招募中的
                .orderByDesc(Group::getCreateTime) // 按发布时间倒序
                .page(page);

        // 4. 返回完整的分页元数据对象，包含 total, pages, records 等
        return page;
    }
}
