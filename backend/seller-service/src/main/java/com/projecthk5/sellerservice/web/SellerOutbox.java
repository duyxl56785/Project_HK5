package com.projecthk5.sellerservice.web;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seller_outbox")
public class SellerOutbox {
    public enum Kind { ROLE_SYNC, NOTIFY }

    @Id
    private UUID id;
    @Column(nullable = false, length = 32)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Kind kind;
    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected SellerOutbox() {}

    public SellerOutbox(Kind kind, UUID sellerId, String payload) {
        this.id = UUID.randomUUID();
        this.kind = kind;
        this.sellerId = sellerId;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public void attempted() { attempts++; }
    public void delivered() { deliveredAt = Instant.now(); }
    public Kind getKind() { return kind; }
    public UUID getSellerId() { return sellerId; }
    public String getPayload() { return payload; }
}
