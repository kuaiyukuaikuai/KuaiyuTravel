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
 * 组团出发实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_group")
public class Group implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键，使用雪花算法生成
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 组团编号 (唯一分享码)
     */
    private String groupNo;

    /**
     * 组团标题
     */
    private String title;

    /**
     * 组团简介
     */
    private String introduction;

    /**
     * 团长ID(发起人)
     */
    private Long leaderId;

    /**
     * 人数上限
     */
    private Integer maxPeople;

    /**
     * 当前人数
     */
    private Integer currentPeople;

    /**
     * 预计出发时间
     */
    private LocalDateTime startTime;

    /**
     * 游玩天数
     */
    private Integer days;

    /**
     * 预计人均预算(单位:分)
     */
    private Integer budget;

    /**
     * 状态：0-招募中，1-已成团/进行中，2-已结束，3-已解散
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}