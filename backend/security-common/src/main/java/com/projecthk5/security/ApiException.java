package com.projecthk5.security;

import org.springframework.http.HttpStatus;

/** Business/security failure rendered as {@code {code, message}} by {@link ApiErrorAdvice}. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    /** Extra response headers, e.g. Retry-After. */
    public java.util.Map<String, String> headers() { return java.util.Map.of(); }

    public static ApiException badRequest(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    public static ApiException forbidden(String message) { return new ApiException(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", message); }
    public static ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
    public static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    public static ApiException unavailable(String code, String message) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message); }
}
