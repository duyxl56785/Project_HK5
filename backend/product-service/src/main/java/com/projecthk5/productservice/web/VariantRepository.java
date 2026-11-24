package com.projecthk5.productservice.web;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VariantRepository extends JpaRepository<ProductExtras.Variant, UUID> {
    List<ProductExtras.Variant> findByGroupIdOrderByPosition(UUID groupId);
    List<ProductExtras.Variant> findByProductIdIn(Collection<UUID> productIds);
}
