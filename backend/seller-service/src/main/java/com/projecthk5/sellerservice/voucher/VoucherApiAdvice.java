package com.projecthk5.sellerservice.voucher;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VoucherApiAdvice {
    @ExceptionHandler(VoucherQuotaException.class)
    ResponseEntity<Map<String, Object>> quota(VoucherQuotaException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", error.code(), "message", error.getMessage(),
                "used", error.used(), "limit", error.limit()));
    }
}
