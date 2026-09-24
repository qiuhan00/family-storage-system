package com.family.storage.common;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：携带 HTTP 状态码与业务错误码。
 * 由 {@link GlobalExceptionHandler} 统一转换为 {@code {code, message}} 响应体。
 */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status.value();
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}