package com.projecthk5.productservice.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A null status keeps the current one on update and means ACTIVE on create. */
public record CategoryRequest(@NotBlank @Size(max = 255) String name, UUID parentId, CategoryStatus status) {
    public CategoryRequest(String name, UUID parentId) {
        this(name, parentId, null);
    }
}
