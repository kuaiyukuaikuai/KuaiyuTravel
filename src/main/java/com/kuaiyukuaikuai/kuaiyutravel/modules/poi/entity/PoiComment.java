package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * POI地点评价表 实体类
 */
@Data
@TableName("tb_poi_comment")
public class PoiComment implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 评价的地点ID */
    private Long poiId;

    /** 评价者用户ID */
    private Long userId;

    /** 评价内容 */
    private String content;

    /** 评分 (1-5星) */
    private Integer score;

    /** 评价时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}