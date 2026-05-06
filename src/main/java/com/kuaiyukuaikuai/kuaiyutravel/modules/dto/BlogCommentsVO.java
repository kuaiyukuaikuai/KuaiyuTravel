package com.kuaiyukuaikuai.kuaiyutravel.modules.dto;

import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.BlogComments;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 博客评论VO类，扩展评论实体，包含用户信息和子评论
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BlogCommentsVO extends BlogComments {
    
    /**
     * 评论者昵称
     */
    @TableField(exist = false)
    private String nickName;
    
    /**
     * 评论者头像
     */
    @TableField(exist = false)
    private String icon;
    
    /**
     * 被回复者昵称
     */
    @TableField(exist = false)
    private String answerNickName;
    
    /**
     * 子评论列表
     */
    @TableField(exist = false)
    private List<BlogCommentsVO> children;
    
    /**
     * 当前用户是否已点赞
     */
    @TableField(exist = false)
    private Boolean isLike;
}