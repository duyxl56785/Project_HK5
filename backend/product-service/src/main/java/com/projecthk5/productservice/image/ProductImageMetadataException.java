package com.projecthk5.productservice.image;

/** Internal reservation failures, not an HTTP error contract. */
public class ProductImageMetadataException extends RuntimeException {
    public enum Code {
        INVALID_INPUT, LIMIT_EXCEEDED, DUPLICATE_IMAGE, OPERATION_CONFLICT,
        STALE_VERSION, NOT_FOUND, CONSTRAINT_VIOLATION, UNAVAILABLE
    }
    private final Code code;

    public ProductImageMetadataException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ProductImageMetadataException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() { return code; }
}
