package com.projecthk5.productservice.image.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Private persistence only; callers must authorize and lock the Product before mutation. */
public interface ProductImageMetadataRepository extends JpaRepository<ProductImageMetadataEntity, UUID> {
    Optional<ProductImageMetadataEntity> findByProductIdAndOperationId(UUID productId, UUID operationId);
    List<ProductImageMetadataEntity> findAllByProductIdAndStateOrderByDisplayOrderAsc(UUID productId, String state);
    List<ProductImageMetadataEntity> findAllByProductIdAndStateInOrderByDisplayOrderAsc(UUID productId, List<String> states);
    Optional<ProductImageMetadataEntity> findByProductIdAndContentSha256AndStateIn(
            UUID productId, String contentSha256, List<String> states);
    Optional<ProductImageMetadataEntity> findByProductIdAndContentSha256AndState(
            UUID productId, String contentSha256, String state);
}
