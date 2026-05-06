package com.kuaiyukuaikuai.kuaiyutravel.modules.my.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 签到记录实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_sign")
public class Sign implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 签到年份
     */
    private Integer year;

    /**
     * 签到月份
     */
    private Integer month;

    /**
     * 签到具体日期
     */
    private LocalDate date;

    /**
     * 是否补签，默认0
     */
    private Integer isBackup;

}