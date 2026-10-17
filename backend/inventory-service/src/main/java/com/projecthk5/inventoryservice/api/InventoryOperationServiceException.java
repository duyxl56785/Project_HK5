package com.projecthk5.inventoryservice.api;

public class InventoryOperationServiceException extends RuntimeException {
    private final String code;

    public InventoryOperationServiceException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
