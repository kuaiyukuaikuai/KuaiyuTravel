package com.kuaiyukuaikuai.kuaiyutravel.common.exception;

import lombok.Getter;

/**
 * 业务错误码枚举
 * 格式: 模块(2位) + 具体错误(2位)
 */
@Getter
public enum ErrorCode {

    // 通用错误 (00xx)
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "资源不存在"),
    SERVER_ERROR(500, "服务器内部错误"),

    // 用户模块 (10xx)
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    USER_PASSWORD_ERROR(1003, "密码错误"),
    USER_CODE_ERROR(1004, "验证码错误或已过期"),
    USER_PHONE_INVALID(1005, "手机号格式不正确"),

    // 组团模块 (20xx)
    GROUP_NOT_FOUND(2001, "组团不存在"),
    GROUP_FULL(2002, "组团人数已满"),
    GROUP_CLOSED(2003, "该组团已关闭或已结束"),
    GROUP_ALREADY_JOINED(2004, "你已经是该团成员"),
    GROUP_NO_PERMISSION(2005, "只有团长可以执行此操作"),
    GROUP_LEADER_CANNOT_EXIT(2006, "团长不能退出组团，请解散组团"),
    GROUP_LEADER_CANNOT_REMOVE_SELF(2007, "团长不能踢出自己"),

    // 优惠券模块 (30xx)
    VOUCHER_NOT_FOUND(3001, "优惠券不存在"),
    VOUCHER_STOCK_EMPTY(3002, "库存不足"),
    VOUCHER_ALREADY_PURCHASED(3003, "你已购买过该优惠券"),
    VOUCHER_ORDER_NOT_FOUND(3004, "订单不存在"),
    VOUCHER_SECKILL_FAILED(3005, "秒杀失败"),

    // 地点模块 (40xx)
    POI_NOT_FOUND(4001, "地点不存在"),
    BLOG_NOT_FOUND(4002, "博客不存在"),
    COMMENT_NOT_FOUND(4003, "评论不存在"),

    // AI模块 (50xx)
    AI_CHAT_ERROR(5001, "AI对话异常"),
    AI_PLAN_ERROR(5002, "行程规划失败"),

    // 系统模块 (90xx)
    SYSTEM_BUSY(9001, "系统繁忙，请稍后再试"),
    SYSTEM_RATE_LIMIT(9002, "请求过于频繁，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
