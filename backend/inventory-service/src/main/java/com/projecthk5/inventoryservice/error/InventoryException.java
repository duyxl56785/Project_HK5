package com.projecthk5.inventoryservice.error;

import org.springframework.http.HttpStatus;

public class InventoryException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public InventoryException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
