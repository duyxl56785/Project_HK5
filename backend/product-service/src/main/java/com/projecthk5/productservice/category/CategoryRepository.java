package com.projecthk5.productservice.category;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    boolean existsByParent_Id(UUID parentId);
}
