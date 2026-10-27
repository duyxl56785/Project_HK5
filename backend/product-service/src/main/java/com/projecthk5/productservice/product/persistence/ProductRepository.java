package com.projecthk5.productservice.product.persistence;

import com.projecthk5.productservice.product.ProductStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID>, ProductCatalogSearch {
    Optional<ProductEntity> findByInventorySku(String inventorySku);

    // Scalar projection bypasses the persistence-context entity cache; Category is local to Product DB.
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    @Query("""
            select p.id as id, p.storeId as storeId, p.categoryId as categoryId,
                   p.inventorySku as inventorySku, p.name as name, p.unitPrice as unitPrice,
                   p.currency as currency, p.status as status, p.reviewReason as reviewReason,
                   case when c.id is null then false else true end as categoryExists
            from ProductEntity p left join Category c on c.id = p.categoryId where p.id in :ids
            """)
    List<CatalogRow> findCatalogRows(@Param("ids") Set<UUID> ids);

    interface CatalogRow {
        UUID getId();
        UUID getStoreId();
        UUID getCategoryId();
        String getInventorySku();
        String getName();
        BigDecimal getUnitPrice();
        String getCurrency();
        ProductStatus getStatus();
        String getReviewReason();
        Boolean getCategoryExists();
    }
}
