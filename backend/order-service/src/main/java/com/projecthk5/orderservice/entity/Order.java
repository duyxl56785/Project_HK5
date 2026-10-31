package com.projecthk5.orderservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="orders")
public class Order {
    @Id private UUID id;
    @Column(name="customer_id",nullable=false,length=200) private String customerId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private Status status;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal total;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="shipping_name",length=120) private String shippingName;
    @Column(name="shipping_phone",length=32) private String shippingPhone;
    @Column(name="shipping_address",length=500) private String shippingAddress;
    @Column(name="payment_method",nullable=false,length=16) private String paymentMethod="COD";
    @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true)
    private List<SellerOrder> sellerOrders=new ArrayList<>();
    public enum Status { PLACED, CANCELLED }
    public record Shipping(String name,String phone,String address) {}
    protected Order() {}
    public Order(UUID id,String customerId) {this.id=id;this.customerId=customerId;this.status=Status.PLACED;this.total=BigDecimal.ZERO;this.createdAt=Instant.now();}
    public UUID getId(){return id;}
    public String getCustomerId(){return customerId;}
    public Status getStatus(){return status;}
    public BigDecimal getTotal(){return total;}
    public Instant getCreatedAt(){return createdAt;}
    public String getPaymentMethod(){return paymentMethod;}
    public Shipping getShipping(){return shippingAddress==null?null:new Shipping(shippingName,shippingPhone,shippingAddress);}
    public List<SellerOrder> getSellerOrders(){return sellerOrders;}
    public void addSellerOrder(SellerOrder so){sellerOrders.add(so);this.total=this.total.add(so.getGross());}
    public void ship(Shipping shipping){
        if (shipping==null) return;
        this.shippingName=shipping.name();this.shippingPhone=shipping.phone();this.shippingAddress=shipping.address();
    }
    /** The customer-visible Order closes once every Store order is cancelled or rejected. */
    public void refreshStatus(){
        if (!sellerOrders.isEmpty() && sellerOrders.stream().allMatch(SellerOrder::isClosed)) this.status=Status.CANCELLED;
    }
}
