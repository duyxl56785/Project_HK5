package com.projecthk5.cartservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "carts")
public class Cart {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 200)
    private String customerId;
    @Version private long revision;
    @Column(length = 128) private String checkoutClaim;
    @Column(nullable = false) private Instant updatedAt = Instant.now();
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {}
    public Cart(String customerId) { this.customerId = customerId; }
    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public long getRevision() { return revision; }
    public String getCheckoutClaim() { return checkoutClaim; }
    public List<CartItem> getItems() { return items; }
    public void touch() { updatedAt = Instant.now(); }
    public void ensureEditable() { if (checkoutClaim != null) throw new IllegalStateException("Cart is reserved for checkout"); }
    public void claim(String key, Long expectedRevision) {
        if (Objects.equals(checkoutClaim, key)) return; // idempotent claim retry
        if (expectedRevision != null && expectedRevision != revision) throw new IllegalStateException("Cart revision changed");
        if (checkoutClaim != null && !checkoutClaim.equals(key)) throw new IllegalStateException("Cart reserved by another checkout");
        if (items.isEmpty()) throw new IllegalStateException("Cart is empty");
        checkoutClaim = key; touch();
    }
    public void release(String key) {
        if (checkoutClaim == null) return; // idempotent release retry
        if (!Objects.equals(checkoutClaim, key)) throw new IllegalStateException("Checkout claim mismatch");
        checkoutClaim = null; touch();
    }
    public void finalizeCheckout(String key) {
        if (checkoutClaim == null && items.isEmpty()) return; // idempotent finalization retry
        if (!Objects.equals(checkoutClaim, key)) throw new IllegalStateException("Checkout claim mismatch");
        items.clear(); checkoutClaim = null; touch();
    }
}
