package com.projecthk5.productservice.web;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<ProductExtras.Review, UUID> {
    List<ProductExtras.Review> findByProductIdAndHiddenFalseOrderByCreatedAtDesc(UUID productId);

    List<ProductExtras.Review> findTop200ByOrderByCreatedAtDesc();

    boolean existsByOrderIdAndProductId(UUID orderId, UUID productId);

    List<ProductExtras.Review> findByCustomerId(String customerId);

    /** Public aggregate excludes hidden reviews from both sum and count (F15). */
    @Query("select count(r), coalesce(avg(r.rating), 0) from Review r where r.productId = :productId and r.hidden = false")
    List<Object[]> publicSummary(UUID productId);
}
