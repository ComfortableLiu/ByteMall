package com.bytemail.common.model.dto;

import lombok.Data;

/**
 * 统一 API 响应体。对照 Node 版各 controller 的返回结构，字段不一致时按前端约定调整
 */
@Data
public class ApiResponse<T> {

    private int code;

    private String message;

    private T data;

    private boolean success;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 0;
        r.success = true;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.success = false;
        r.message = message;
        return r;
    }
}
