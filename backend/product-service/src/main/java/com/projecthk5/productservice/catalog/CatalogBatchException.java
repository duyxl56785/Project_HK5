package com.projecthk5.productservice.catalog;

public class CatalogBatchException extends RuntimeException {
    public enum Code { INVALID_REQUEST, DEPENDENCY_UNAVAILABLE }
    private final Code code;

    public CatalogBatchException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() { return code; }
}
