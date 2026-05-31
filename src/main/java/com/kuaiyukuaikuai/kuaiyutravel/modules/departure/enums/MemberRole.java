package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.enums;

import lombok.Getter;

/**
 * 团成员角色枚举
 */
@Getter
public enum MemberRole {

    LEADER(0, "团长"),
    MEMBER(1, "普通成员");

    private final int code;
    private final String desc;

    MemberRole(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
