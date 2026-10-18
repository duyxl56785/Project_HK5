package com.projecthk5.productservice.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CategoryRequest(@NotBlank @Size(max = 255) String name, UUID parentId) {
}
