package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("tb_blog")
public class Blog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @NotNull(message = "地点ID不能为空")
    private Long poiId;

    private Long userId;

    @TableField(exist = false)
    private String icon;

    @TableField(exist = false)
    private String name;

    @TableField(exist = false)
    private Boolean isLike;

    @NotBlank(message = "博客标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100个字符")
    private String title;

    @Size(max = 1000, message = "图片链接长度不能超过1000个字符")
    private String images;

    @NotBlank(message = "博客内容不能为空")
    @Size(max = 2000, message = "内容长度不能超过2000个字符")
    private String content;

    private Integer liked;

    private Integer comments;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
