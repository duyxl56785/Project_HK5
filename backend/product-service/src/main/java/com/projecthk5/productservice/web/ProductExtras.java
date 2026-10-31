package com.projecthk5.productservice.web;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA rows owned by the HTTP layer: description, stored images and reviews (V4 migration). */
public final class ProductExtras {
    private ProductExtras() {}

    @Entity(name = "Details")
    @Table(name = "product_details")
    public static class Details {
        @Id
        @Column(name = "product_id")
        private UUID productId;
        @Column(nullable = false, length = 5000)
        private String description;

        protected Details() {}
        public Details(UUID productId, String description) { this.productId = productId; this.description = description; }
        public UUID getProductId() { return productId; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    @Entity(name = "Image")
    @Table(name = "product_images")
    public static class Image {
        @Id
        private UUID id;
        @Column(name = "product_id", nullable = false)
        private UUID productId;
        @Column(name = "storage_key", nullable = false, length = 36)
        private String storageKey;
        @Column(name = "media_type", nullable = false, length = 16)
        private String mediaType;
        @Column(nullable = false)
        private int width;
        @Column(nullable = false)
        private int height;
        @Column(name = "size_bytes", nullable = false)
        private int sizeBytes;
        @Column(name = "display_order", nullable = false)
        private int displayOrder;
        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        protected Image() {}
        public Image(UUID productId, String storageKey, String mediaType, int width, int height, int sizeBytes, int displayOrder) {
            this.id = UUID.randomUUID();
            this.productId = productId;
            this.storageKey = storageKey;
            this.mediaType = mediaType;
            this.width = width;
            this.height = height;
            this.sizeBytes = sizeBytes;
            this.displayOrder = displayOrder;
            this.createdAt = Instant.now();
        }
        public UUID getId() { return id; }
        public UUID getProductId() { return productId; }
        public String getStorageKey() { return storageKey; }
        public String getMediaType() { return mediaType; }
        public int getDisplayOrder() { return displayOrder; }
    }

    @Entity(name = "Review")
    @Table(name = "product_reviews")
    public static class Review {
        @Id
        private UUID id;
        @Column(name = "order_id", nullable = false)
        private UUID orderId;
        @Column(name = "product_id", nullable = false)
        private UUID productId;
        @Column(name = "customer_id", nullable = false, length = 128)
        private String customerId;
        @Column(nullable = false)
        private int rating;
        @Column(length = 2000)
        private String comment;
        @Column(nullable = false)
        private boolean hidden;
        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        protected Review() {}
        public Review(UUID orderId, UUID productId, String customerId, int rating, String comment) {
            this.id = UUID.randomUUID();
            this.orderId = orderId;
            this.productId = productId;
            this.customerId = customerId;
            this.rating = rating;
            this.comment = comment;
            this.createdAt = Instant.now();
        }
        public UUID getId() { return id; }
        public UUID getOrderId() { return orderId; }
        public UUID getProductId() { return productId; }
        public String getCustomerId() { return customerId; }
        public int getRating() { return rating; }
        public String getComment() { return comment; }
        public boolean isHidden() { return hidden; }
        public void setHidden(boolean hidden) { this.hidden = hidden; }
        public Instant getCreatedAt() { return createdAt; }
    }
}
