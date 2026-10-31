package com.projecthk5.userservice.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_profiles")
public class UserProfile {
    @Id
    @Column(name = "account_id", length = 128)
    private String accountId;
    @Column(name = "display_name", length = 100)
    private String displayName;
    @Column(length = 16)
    private String phone;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {}

    public UserProfile(String accountId) {
        this.accountId = accountId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String displayName, String phone) {
        this.displayName = displayName;
        this.phone = phone;
        this.updatedAt = Instant.now();
    }

    public String getAccountId() { return accountId; }
    public String getDisplayName() { return displayName; }
    public String getPhone() { return phone; }
}
