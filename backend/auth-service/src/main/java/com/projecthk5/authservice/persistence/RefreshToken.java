package com.projecthk5.authservice.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    private UUID id;
    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;
    @Column(name = "parent_id", updatable = false)
    private UUID parentId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {}

    public RefreshToken(UUID id, UUID familyId, String tokenHash, UUID parentId,
                        Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.parentId = parentId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public String getTokenHash() { return tokenHash; }
    public UUID getParentId() { return parentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
