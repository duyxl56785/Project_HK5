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
    @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true)
    private List<SellerOrder> sellerOrders=new ArrayList<>();
    public enum Status { PLACED, CANCELLED }
    protected Order() {}
    public Order(UUID id,String customerId) {this.id=id;this.customerId=customerId;this.status=Status.PLACED;this.total=BigDecimal.ZERO;this.createdAt=Instant.now();}
    public UUID getId(){return id;}
    public String getCustomerId(){return customerId;}
    public Status getStatus(){return status;}
    public BigDecimal getTotal(){return total;}
    public List<SellerOrder> getSellerOrders(){return sellerOrders;}
    public void addSellerOrder(SellerOrder so){sellerOrders.add(so);this.total=this.total.add(so.getGross());}
}
