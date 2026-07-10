package com.pzhu.eduadmin.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结构：{ code, message, data }，与 docs/08-开发指南.md §5 联调约定保持一致。
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(0);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }
}
