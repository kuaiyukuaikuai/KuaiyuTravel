package com.kuaiyukuaikuai.kuaiyutravel.common.exception;

import lombok.Getter;

/**
 * 业务异常
 * 用于业务逻辑校验失败时抛出，全局异常处理器会统一处理为 Result 返回
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
