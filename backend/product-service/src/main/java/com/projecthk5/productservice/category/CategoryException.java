package com.projecthk5.productservice.category;

import org.springframework.http.HttpStatus;

public class CategoryException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    CategoryException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus getStatus() {
        return status;
    }

    String getCode() {
        return code;
    }
}
