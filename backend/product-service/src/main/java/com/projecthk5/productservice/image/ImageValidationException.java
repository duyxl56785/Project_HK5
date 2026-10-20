package com.projecthk5.productservice.image;

public class ImageValidationException extends RuntimeException {
    public enum Code {
        IMAGE_INVALID_INPUT, IMAGE_EMPTY, IMAGE_TOO_LARGE, IMAGE_UNSUPPORTED_TYPE,
        IMAGE_TYPE_MISMATCH, IMAGE_INVALID_CONTENT, IMAGE_COUNT_EXCEEDED, IMAGE_RESOURCE_LIMIT
    }

    private final Code code;

    public ImageValidationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() { return code; }
}
