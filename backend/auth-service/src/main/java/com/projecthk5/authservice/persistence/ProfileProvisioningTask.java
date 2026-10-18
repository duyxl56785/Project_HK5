package com.projecthk5.authservice.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile_provisioning_tasks")
public class ProfileProvisioningTask {
    @Id
    @Column(name = "account_id")
    private UUID accountId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected ProfileProvisioningTask() {}

    public ProfileProvisioningTask(UUID accountId, Instant createdAt) {
        this.accountId = accountId;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
    }

    public UUID getAccountId() { return accountId; }
    public Instant getCreatedAt() { return createdAt; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCompletedAt() { return completedAt; }
}
