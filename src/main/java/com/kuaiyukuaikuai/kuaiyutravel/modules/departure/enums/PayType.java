package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.enums;

import lombok.Getter;

/**
 * 支付方式枚举
 */
@Getter
public enum PayType {

    BALANCE(1, "余额支付"),
    ALIPAY(2, "支付宝"),
    WECHAT(3, "微信支付");

    private final int code;
    private final String desc;

    PayType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
