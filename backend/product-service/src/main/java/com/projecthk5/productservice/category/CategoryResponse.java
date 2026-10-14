package com.projecthk5.productservice.category;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, UUID parentId) {
}
