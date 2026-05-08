package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.vo.UserDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 评价展示对象
 * 继承 UserDTO 后，自动拥有了 id(用户ID), nickName(昵称), icon(头像)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PoiCommentVO extends UserDTO {

    /** 评价记录的唯一ID (避免和继承自UserDTO的userId冲突) */
    private Long commentId;

    /** 评价内容 */
    private String content;

    /** 评分(1-5) */
    private Integer score;

    /** 评价时间 (格式化为标准时间字符串返回给前端) */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}