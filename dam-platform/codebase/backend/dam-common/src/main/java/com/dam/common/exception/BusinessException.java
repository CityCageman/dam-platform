package com.dam.common.exception;

import lombok.Getter;

/**
 * 业务异常基类
 * 业务规则校验失败时抛出，由 GlobalExceptionHandler 统一捕获并转换为 Result 返回
 * 区别于系统异常：携带明确的状态码与用户可读消息
 */
@Getter
public class BusinessException extends RuntimeException {
    /** 业务错误状态码 */
    private final int code;

    /**
     * 构造业务异常（指定状态码与消息）
     *
     * @param code    错误状态码
     * @param message 错误描述
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造业务异常（默认 500 状态码）
     *
     * @param message 错误描述
     */
    public BusinessException(String message) {
        this(500, message);
    }
}