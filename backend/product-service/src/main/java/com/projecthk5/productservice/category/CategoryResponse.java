package com.projecthk5.productservice.category;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, UUID parentId) {
    static CategoryResponse from(Category category) {
        Category parent = category.getParent();
        return new CategoryResponse(category.getId(), category.getName(),
                parent == null ? null : parent.getId());
    }
}
