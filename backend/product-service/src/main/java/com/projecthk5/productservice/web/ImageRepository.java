package com.projecthk5.productservice.web;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<ProductExtras.Image, UUID> {
    List<ProductExtras.Image> findByProductIdOrderByDisplayOrder(UUID productId);
}
