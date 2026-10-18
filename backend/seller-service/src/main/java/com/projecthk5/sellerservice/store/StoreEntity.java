package com.projecthk5.sellerservice.store;

import com.projecthk5.sellerservice.seller.entity.SellerProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "stores", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stores_seller_id", columnNames = "seller_id"),
        @UniqueConstraint(name = "uk_stores_slug", columnNames = "slug")
})
public class StoreEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_stores_seller"))
    private SellerProfile seller;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 128)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StoreStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StoreEntity() { }

    public static StoreEntity create(StoreProfile domain, SellerProfile seller) {
        if (domain == null || seller == null || !domain.sellerId().equals(seller.getId())
                || !domain.ownerReference().equals(seller.getOwnerReference())) {
            throw invalid("Store must belong to its persisted seller");
        }
        StoreEntity entity = new StoreEntity();
        entity.id = domain.id();
        entity.seller = seller;
        entity.apply(domain);
        return entity;
    }

    public void apply(StoreProfile domain) {
        if (domain == null || !id.equals(domain.id()) || !getSellerId().equals(domain.sellerId())
                || !seller.getOwnerReference().equals(domain.ownerReference())) {
            throw invalid("Store identity and seller ownership must remain unchanged");
        }
        name = domain.name();
        slug = domain.slug();
        status = domain.open() ? StoreStatus.OPEN : StoreStatus.CLOSED;
    }

    public StoreProfile toDomain(String verifiedOwnerReference) {
        if (!seller.getOwnerReference().equals(verifiedOwnerReference)) {
            throw invalid("Store owner must match its persisted seller");
        }
        return new StoreProfile(id, getSellerId(), verifiedOwnerReference, name, slug, status == StoreStatus.OPEN);
    }

    @PrePersist
    private void initializeTimestamps() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        updatedAt = createdAt;
    }

    @PreUpdate
    private void updateTimestamp() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        // A backwards wall clock must not violate the persisted timestamp ordering.
        if (now.isAfter(updatedAt)) {
            updatedAt = now;
        }
    }

    public UUID getId() { return id; }
    public UUID getSellerId() { return seller.getId(); }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public StoreStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private static StoreRuleException invalid(String message) {
        return new StoreRuleException(StoreRuleException.Code.INVALID_INPUT, message);
    }
}
