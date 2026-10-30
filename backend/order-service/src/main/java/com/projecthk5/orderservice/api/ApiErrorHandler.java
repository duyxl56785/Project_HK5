package com.projecthk5.orderservice.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(com.projecthk5.security.ApiException.class)
    public ResponseEntity<Map<String,String>> handleApi(com.projecthk5.security.ApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("code", e.code(), "message", e.getMessage()));
    }
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String,String>> handleStale(org.springframework.dao.OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "ORDER_VERSION_CONFLICT", "message", "Order changed concurrently; reload and retry"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> handleStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("code", "REQUEST_REJECTED", "message", e.getReason() == null ? "Request rejected" : e.getReason()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", "VALIDATION_FAILED", "message", "Invalid request"));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String,String>> handleBusiness(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "BUSINESS_CONFLICT", "message", e.getMessage() == null ? "Conflict" : e.getMessage()));
    }
}
