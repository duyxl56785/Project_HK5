package com.projecthk5.productservice.product.persistence;

/** Stable internal failures; database details must not become a public response contract. */
public class ProductPersistenceException extends RuntimeException {
    public enum Code {
        DUPLICATE_SKU, CATEGORY_NOT_FOUND, NOT_FOUND, IMMUTABLE_IDENTITY,
        CONSTRAINT_VIOLATION, UNAVAILABLE, STALE_VERSION
    }

    private final Code code;

    public ProductPersistenceException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ProductPersistenceException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() { return code; }
}
