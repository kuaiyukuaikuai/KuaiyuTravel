package com.kuaiyukuaikuai.kuaiyutravel.common.utils;

import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一响应结果封装
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 是否成功 */
    private boolean success;

    /** 业务错误码 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    /** 分页总数 */
    private Long total;

    /** 时间戳 */
    private Long timestamp;

    public static <T> Result<T> ok() {
        return new Result<>(true, ErrorCode.SUCCESS.getCode(), null, null, null, System.currentTimeMillis());
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(true, ErrorCode.SUCCESS.getCode(), null, data, null, System.currentTimeMillis());
    }

    public static <T> Result<T> ok(List<T> data, Long total) {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setCode(ErrorCode.SUCCESS.getCode());
        result.setData((T) data);
        result.setTotal(total);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(false, ErrorCode.SERVER_ERROR.getCode(), message, null, null, System.currentTimeMillis());
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(false, code, message, null, null, System.currentTimeMillis());
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(false, errorCode.getCode(), errorCode.getMessage(), null, null, System.currentTimeMillis());
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(false, errorCode.getCode(), message, null, null, System.currentTimeMillis());
    }
}
