package com.projecthk5.orderservice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="checkout_attempts",uniqueConstraints=@UniqueConstraint(name="uq_checkout_customer_key",columnNames={"customer_id","idempotency_key"}))
public class CheckoutAttempt {
    @Id private UUID id;
    @Column(name="customer_id",nullable=false,length=200) private String customerId;
    @Column(name="idempotency_key",nullable=false,length=128) private String idempotencyKey;
    @Column(nullable=false,length=64) private String fingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private State state;
    @Column(name="order_id") private UUID orderId;
    @Column(nullable=false) private Instant createdAt;
    public enum State { PROCESSING, SUCCEEDED, FAILED, NEEDS_RECONCILIATION }
    protected CheckoutAttempt() {}
    public CheckoutAttempt(String customerId,String key,String fingerprint) {
        this.id=UUID.randomUUID();this.customerId=customerId;this.idempotencyKey=key;
        this.fingerprint=fingerprint;this.state=State.PROCESSING;this.createdAt=Instant.now();
    }
    public UUID getId(){return id;}
    public String getCustomerId(){return customerId;}
    public String getIdempotencyKey(){return idempotencyKey;}
    public String getFingerprint(){return fingerprint;}
    public State getState(){return state;}
    public UUID getOrderId(){return orderId;}
    public void succeed(UUID orderId){this.orderId=orderId;this.state=State.SUCCEEDED;}
    public void fail(boolean uncertain){this.state=uncertain?State.NEEDS_RECONCILIATION:State.FAILED;}
}
