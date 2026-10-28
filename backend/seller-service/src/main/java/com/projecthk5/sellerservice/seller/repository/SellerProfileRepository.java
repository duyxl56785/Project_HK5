package com.projecthk5.sellerservice.seller.repository;

import com.projecthk5.sellerservice.seller.entity.SellerProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, UUID> {
    Optional<SellerProfile> findByOwnerReference(String ownerReference);

    @Modifying
    @Query(value = """
            INSERT INTO seller_status_outbox(event_id, seller_id, owner_reference, seller_status, source_version)
            SELECT :eventId, id, owner_reference, status, version
            FROM seller_profiles WHERE id = :sellerId AND version = :sourceVersion
            """, nativeQuery = true)
    int appendStatusSnapshot(@Param("eventId") UUID eventId, @Param("sellerId") UUID sellerId,
                             @Param("sourceVersion") long sourceVersion);
}
