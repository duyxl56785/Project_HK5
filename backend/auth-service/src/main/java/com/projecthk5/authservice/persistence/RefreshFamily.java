package com.projecthk5.authservice.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_families")
public class RefreshFamily {
    @Id
    private UUID id;
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshFamily() {}

    public RefreshFamily(UUID id, UUID accountId, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.accountId = accountId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    // Rotation, reuse and logout must acquire the same family lock before changing session state.
    public void revoke(Instant now) {
        if (revokedAt == null) { revokedAt = now; }
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
