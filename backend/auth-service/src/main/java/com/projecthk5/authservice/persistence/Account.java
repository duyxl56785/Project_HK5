package com.projecthk5.authservice.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "accounts")
public class Account {
    public enum Role { CUSTOMER, SELLER, ADMIN }

    @Id
    private UUID id;
    @Column(nullable = false, length = 254, updatable = false)
    private String email;
    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    private String passwordHash;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "seller_role_revision", nullable = false)
    private long sellerRoleRevision = -1;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_role_grants", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "role", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    protected Account() {}

    public Account(UUID id, String email, String passwordHash, String status, Set<Role> roles, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.email = normalizeEmail(email);
        if (this.email.isBlank() || this.email.length() > 254) {
            throw new IllegalArgumentException("Invalid email length");
        }
        if (passwordHash == null || passwordHash.isBlank() || status == null || status.isBlank()) {
            throw new IllegalArgumentException("Password hash and explicit account status are required");
        }
        this.passwordHash = passwordHash;
        this.status = status;
        this.roles = new HashSet<>(roles);
        if (this.roles.isEmpty() || this.roles.contains(null)) {
            throw new IllegalArgumentException("At least one valid role is required");
        }
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email).strip().toLowerCase(Locale.ROOT);
    }

    /** Role grants are idempotent; an account always keeps at least CUSTOMER. */
    public boolean grant(Role role) { return roles.add(Objects.requireNonNull(role)); }
    public boolean revoke(Role role) { return role != Role.CUSTOMER && roles.remove(role); }

    /** Applies a Seller status event once; replayed or out-of-order revisions change nothing. */
    public void applySellerRole(boolean granted, long revision) {
        if (revision <= sellerRoleRevision) return;
        sellerRoleRevision = revision;
        if (granted) grant(Role.SELLER); else revoke(Role.SELLER);
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getStatus() { return status; }
    public Set<Role> getRoles() { return Set.copyOf(roles); }
    public Instant getCreatedAt() { return createdAt; }
}
