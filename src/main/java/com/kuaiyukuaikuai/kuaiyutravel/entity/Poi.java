package com.kuaiyukuaikuai.kuaiyutravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
* 
* @TableName tb_poi
*/
@Data
public class Poi implements Serializable {

    /**
    * 主键
    */
    @TableId(value = "id", type = IdType.AUTO)
    @NotNull(message="[主键]不能为空")
    @Schema(description = "主键")
    private Long id;
    /**
    * 地点名称
    */
    @NotBlank(message="[地点名称]不能为空")
    @Size(max= 128,message="编码长度不能超过128")
    @Schema(description = "地点名称")
    private String name;
    /**
    * 地点类型的id
    */
    @NotNull(message="[地点类型的id]不能为空")
    @Schema(description = "地点类型的id")
    private Long typeId;
    /**
    * 地点图片，多个图片以','隔开
    */
    @NotBlank(message="[地点图片，多个图片以','隔开]不能为空")
    @Size(max= 1024,message="编码长度不能超过1024")
    @Schema(description = "地点图片，多个图片以','隔开")
    private String images;
    /**
    * 商圈，例如陆家嘴
    */
    @Size(max= 128,message="编码长度不能超过128")
    @Schema(description = "商圈，例如陆家嘴")
    private String area;
    /**
    * 地址
    */
    @NotBlank(message="[地址]不能为空")
    @Size(max= 255,message="编码长度不能超过255")
    @Schema(description = "地址")
    private String address;
    /**
    * 经度
    */
    @NotNull(message="[经度]不能为空")
    @Schema(description = "经度")
    private Double x;
    /**
    * 维度
    */
    @NotNull(message="[维度]不能为空")
    @Schema(description = "维度")
    private Double y;
    /**
    * 均价，取整数
    */
    @Schema(description = "均价，取整数")
    private Long avgPrice;
    /**
    * 销量
    */
    @NotNull(message="[销量]不能为空")
    @Schema(description = "销量")
    private Integer sold;
    /**
    * 评论数量
    */
    @NotNull(message="[评论数量]不能为空")
    @Schema(description = "评论数量")
    private Integer comments;
    /**
    * 评分，1~5分，乘10保存，避免小数
    */
    @NotNull(message="[评分，1~5分，乘10保存，避免小数]不能为空")
    @Schema(description = "评分，1~5分，乘10保存，避免小数")
    private Integer score;
    /**
    * 营业时间，例如 10:00-22:00
    */
    @Size(max= 32,message="编码长度不能超过32")
    @Schema(description = "营业时间，例如 10:00-22:00")
    private String openHours;
    /**
    * 创建时间
    */
    @Schema(description = "创建时间")
    private Date createTime;
    /**
    * 更新时间
    */
    @Schema(description = "更新时间")
    private Date updateTime;

    @TableField(exist = false)
    private Double distance;
}
