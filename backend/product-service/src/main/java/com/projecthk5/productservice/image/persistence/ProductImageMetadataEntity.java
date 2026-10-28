package com.projecthk5.productservice.image.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Durable image reservation and private storage binding. */
@Entity
@Table(name = "product_image_metadata")
public class ProductImageMetadataEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;
    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;
    @Column(name = "content_sha256", nullable = false, updatable = false, columnDefinition = "text")
    private String contentSha256;
    @Column(name = "media_type", nullable = false, updatable = false, columnDefinition = "text")
    private String mediaType;
    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;
    @Column(nullable = false, updatable = false)
    private int width;
    @Column(nullable = false, updatable = false)
    private int height;
    @Column(name = "display_order")
    private Integer displayOrder;
    @Column(nullable = false, columnDefinition = "text")
    private String state;
    @Column(name = "storage_key", columnDefinition = "text")
    private String storageKey;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "ready_at")
    private Instant readyAt;
    @Version
    @Column(nullable = false)
    private Long version;

    protected ProductImageMetadataEntity() { }

    public static ProductImageMetadataEntity pending(UUID id, UUID productId, UUID operationId,
            String contentSha256, String mediaType, int sizeBytes, int width, int height,
            int displayOrder, Instant createdAt) {
        var image = new ProductImageMetadataEntity();
        image.id = Objects.requireNonNull(id);
        image.productId = Objects.requireNonNull(productId);
        image.operationId = Objects.requireNonNull(operationId);
        image.contentSha256 = Objects.requireNonNull(contentSha256);
        image.mediaType = Objects.requireNonNull(mediaType);
        image.sizeBytes = sizeBytes;
        image.width = width;
        image.height = height;
        image.displayOrder = displayOrder;
        image.state = "PENDING_STORAGE";
        // PostgreSQL timestamptz stores microseconds; keep first response identical to a durable retry.
        image.createdAt = Objects.requireNonNull(createdAt).truncatedTo(ChronoUnit.MICROS);
        return image;
    }

    public void ready(String key, Instant at) {
        if (!"PENDING_STORAGE".equals(state) || !id.toString().equals(key)) {
            throw new IllegalStateException("Image must bind its own pending reservation");
        }
        storageKey = key;
        readyAt = Objects.requireNonNull(at).truncatedTo(ChronoUnit.MICROS);
        state = "READY";
    }

    public void cancel(Instant at) {
        if ("CANCELLED".equals(state)) return;
        cancelledAt = Objects.requireNonNull(at).truncatedTo(ChronoUnit.MICROS);
        state = "CANCELLED";
        displayOrder = null;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public UUID getOperationId() { return operationId; }
    public String getContentSha256() { return contentSha256; }
    public String getMediaType() { return mediaType; }
    public int getSizeBytes() { return sizeBytes; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Integer getDisplayOrder() { return displayOrder; }
    public String getState() { return state; }
    public String getStorageKey() { return storageKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getReadyAt() { return readyAt; }
    public Long getVersion() { return version; }
}
