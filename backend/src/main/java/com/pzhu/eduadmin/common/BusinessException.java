package com.pzhu.eduadmin.common;

import lombok.Getter;

/**
 * 业务异常：携带错误码和面向用户的提示文案，由 GlobalExceptionHandler 统一捕获。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
