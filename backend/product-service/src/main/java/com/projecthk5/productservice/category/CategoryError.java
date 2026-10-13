package com.projecthk5.productservice.category;

import java.util.Map;

public record CategoryError(String code, String message, Map<String, String> errors) {
    public CategoryError(String code, String message) {
        this(code, message, Map.of());
    }
}
