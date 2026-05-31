package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.enums;

import lombok.Getter;

/**
 * 优惠券订单状态枚举
 */
@Getter
public enum OrderStatus {

    UNPAID(1, "未支付"),
    PAID(2, "已支付"),
    USED(3, "已核销"),
    CANCELLED(4, "已取消"),
    REFUNDING(5, "退款中"),
    REFUNDED(6, "已退款");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
