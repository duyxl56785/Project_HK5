package com.projecthk5.sellerservice.store;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<StoreEntity, UUID> {
    @Query("select store from StoreEntity store where store.seller.id = :sellerId")
    Optional<StoreEntity> findBySellerId(@Param("sellerId") UUID sellerId);

    Optional<StoreEntity> findBySlug(String slug);

    // Scalar constructor projections read both rows in one statement, bypassing stale managed entities.
    @Query("select new com.projecthk5.sellerservice.store.StoreCurrentSource$Facts("
            + "s.id, p.id, s.status, p.status, s.version, p.version) "
            + "from StoreEntity s join s.seller p where s.id = :id")
    Optional<StoreCurrentSource.Facts> findFactsById(@Param("id") UUID id);

    @Query("select new com.projecthk5.sellerservice.store.StoreCurrentSource$Facts("
            + "s.id, p.id, s.status, p.status, s.version, p.version) "
            + "from StoreEntity s join s.seller p where p.id = :sellerId")
    Optional<StoreCurrentSource.Facts> findFactsBySellerId(@Param("sellerId") UUID sellerId);

    @Query("select new com.projecthk5.sellerservice.store.StoreCurrentSource$Facts("
            + "s.id, p.id, s.status, p.status, s.version, p.version) "
            + "from StoreEntity s join s.seller p where s.slug = :slug")
    Optional<StoreCurrentSource.Facts> findFactsBySlug(@Param("slug") String slug);
}
