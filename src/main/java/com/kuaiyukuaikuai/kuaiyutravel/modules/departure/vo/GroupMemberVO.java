package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.vo;

import com.kuaiyukuaikuai.kuaiyutravel.modules.dto.UserDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 组团成员展示层对象 (VO)
 * 继承 UserDTO，自动获得 id, nickName, icon 属性，
 * 并补充成员在当前团中的特有属性。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GroupMemberVO extends UserDTO {

    /**
     * 在该团中的角色：0-团长，1-普通成员
     */
    private Integer role;

    /**
     * 加入团的时间
     */
    private LocalDateTime joinTime;
}