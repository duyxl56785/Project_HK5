package com.projecthk5.inventoryservice.api;

import com.projecthk5.inventoryservice.model.InsufficientReservationException;
import com.projecthk5.inventoryservice.model.InsufficientStockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class InventoryExceptionHandler {
    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> insufficientStock(InsufficientStockException ex) {
        return Map.of("code", "INSUFFICIENT_STOCK", "sku", ex.getSku(),
                "available", ex.getAvailable(), "requested", ex.getRequested());
    }

    @ExceptionHandler(InsufficientReservationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> insufficientReservation(InsufficientReservationException ex) {
        return Map.of("code", "INSUFFICIENT_RESERVATION", "sku", ex.getSku(),
                "reserved", ex.getReserved(), "requested", ex.getRequested());
    }

    @ExceptionHandler(InventoryOperationServiceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> operationConflict(InventoryOperationServiceException ex) {
        return Map.of("code", ex.getCode(), "message", ex.getMessage());
    }

    @ExceptionHandler(InventoryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(InventoryNotFoundException ex) {
        return Map.of("code", "INVENTORY_ITEM_NOT_FOUND", "message", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> invalidRequest(MethodArgumentNotValidException ex) {
        return Map.of("code", "INVALID_REQUEST", "message", "Request validation failed");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> invalidArgument(IllegalArgumentException ex) {
        return Map.of("code", "INVALID_REQUEST", "message", "Request contains an invalid value");
    }

    @ExceptionHandler(ArithmeticException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> quantityOverflow(ArithmeticException ex) {
        return Map.of("code", "QUANTITY_OVERFLOW", "message", "Quantity exceeds the supported range");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> dataConflict(DataIntegrityViolationException ex) {
        return Map.of("code", "INVENTORY_CONFLICT", "message", "Inventory operation conflicts with existing data");
    }
}
