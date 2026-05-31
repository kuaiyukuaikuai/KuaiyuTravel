package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_group")
public class Group implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String groupNo;

    @NotBlank(message = "组团标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100个字符")
    private String title;

    @Size(max = 500, message = "简介长度不能超过500个字符")
    private String introduction;

    private Long leaderId;

    @NotNull(message = "目的地ID不能为空")
    private Long poiId;

    private Integer maxPeople;

    private Integer currentPeople;

    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /**
     * 游玩天数
     */
    private Integer days;

    /**
     * 预计人均预算(单位:分)
     */
    private Integer budget;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
