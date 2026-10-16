package com.projecthk5.inventoryservice.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class InventoryExceptionHandler {
    @ExceptionHandler(InventoryException.class)
    public ResponseEntity<ErrorResponse> handle(InventoryException exception) {
        return ResponseEntity.status(exception.getStatus()).body(
                new ErrorResponse(exception.getCode(), exception.getMessage(), Instant.now().toString()));
    }

    public record ErrorResponse(String code, String message, String timestamp) {}
}
