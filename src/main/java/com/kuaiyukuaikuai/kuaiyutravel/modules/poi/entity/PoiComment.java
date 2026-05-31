package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_poi_comment")
public class PoiComment implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @NotNull(message = "地点ID不能为空")
    private Long poiId;

    private Long userId;

    @NotBlank(message = "评价内容不能为空")
    @Size(max = 500, message = "评价内容长度不能超过500个字符")
    private String content;

    @Min(value = 1, message = "评分最低为1星")
    @Max(value = 5, message = "评分最高为5星")
    private Integer score;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
