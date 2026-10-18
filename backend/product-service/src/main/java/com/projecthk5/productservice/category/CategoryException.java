package com.projecthk5.productservice.category;

public class CategoryException extends RuntimeException {
    public enum Code {
        VALIDATION_ERROR,
        CATEGORY_NOT_FOUND,
        CATEGORY_PARENT_NOT_FOUND,
        CATEGORY_CYCLE,
        CATEGORY_HAS_CHILDREN
    }

    private final Code code;

    CategoryException(Code code, String message) {
        super(message);
        this.code = code;
    }

    Code getCode() {
        return code;
    }
}
