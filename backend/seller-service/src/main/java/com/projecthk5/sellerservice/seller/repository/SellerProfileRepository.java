package com.projecthk5.sellerservice.seller.repository;

import com.projecthk5.sellerservice.seller.entity.SellerProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, UUID> {
    Optional<SellerProfile> findByOwnerReference(String ownerReference);
}
