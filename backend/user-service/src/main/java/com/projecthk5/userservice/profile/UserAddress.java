package com.projecthk5.userservice.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_addresses")
public class UserAddress {
    @Id
    private UUID id;
    @Column(name = "account_id", nullable = false, length = 128, updatable = false)
    private String accountId;
    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;
    @Column(nullable = false, length = 16)
    private String phone;
    @Column(nullable = false)
    private String line;
    @Column(nullable = false, length = 100)
    private String city;
    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAddress() {}

    public UserAddress(String accountId, String recipientName, String phone, String line, String city) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.createdAt = Instant.now();
        update(recipientName, phone, line, city);
    }

    public void update(String recipientName, String phone, String line, String city) {
        this.recipientName = recipientName.strip();
        this.phone = phone;
        this.line = line.strip();
        this.city = city.strip();
    }

    public void setDefault(boolean value) { this.defaultAddress = value; }
    public UUID getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getLine() { return line; }
    public String getCity() { return city; }
    public boolean isDefault() { return defaultAddress; }
}
