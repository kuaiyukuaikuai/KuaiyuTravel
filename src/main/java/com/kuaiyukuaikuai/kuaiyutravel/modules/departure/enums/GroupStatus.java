package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.enums;

import lombok.Getter;

/**
 * 组团状态枚举
 */
@Getter
public enum GroupStatus {

    RECRUITING(0, "招募中"),
    IN_PROGRESS(1, "已成团/进行中"),
    FINISHED(2, "已结束"),
    DISBANDED(3, "已解散");

    private final int code;
    private final String desc;

    GroupStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String getDescByCode(int code) {
        for (GroupStatus status : values()) {
            if (status.code == code) {
                return status.desc;
            }
        }
        return "未知状态";
    }
}
