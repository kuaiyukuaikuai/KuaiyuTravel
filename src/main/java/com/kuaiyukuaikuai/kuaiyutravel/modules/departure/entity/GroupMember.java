package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 团组成员实体类
 * <p>
 * 用于存储旅行团成员信息，记录用户与旅行团的关联关系
 *
 * @author system
 * @since 2026-05-07
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_group_member")
public class GroupMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属旅行团ID
     */
    private Long groupId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色标识：0-普通成员，1-管理员，2-团长
     */
    private Integer role;

    /**
     * 加入时间
     */
    private LocalDateTime joinTime;
}