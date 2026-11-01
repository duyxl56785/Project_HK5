package com.projecthk5.security;

import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Shared {code, message, errors?} rendering; never echoes rejected values. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiErrorAdvice {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        var response = ResponseEntity.status(e.status());
        e.headers().forEach(response::header);
        return response.body(Map.of("code", e.code(), "message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage()))).toList();
        return ResponseEntity.badRequest().body(Map.of("code", "VALIDATION_FAILED", "message", "Invalid request", "errors", errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> unreadable(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", "VALIDATION_FAILED", "message", "Malformed request"));
    }
}
