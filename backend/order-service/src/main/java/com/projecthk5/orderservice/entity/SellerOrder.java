package com.projecthk5.orderservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name="seller_orders")
public class SellerOrder {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private Order order;
    @Column(name="seller_id",nullable=false,length=200) private String sellerId;
    @Column(name="store_id",nullable=false,length=200) private String storeId;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal gross;
    @Column(name="platform_fee",nullable=false,precision=19,scale=2) private BigDecimal platformFee;
    @OneToMany(mappedBy="sellerOrder",cascade=CascadeType.ALL,orphanRemoval=true)
    private List<OrderItem> items=new ArrayList<>();
    protected SellerOrder() {}
    public SellerOrder(Order order,String sellerId,String storeId,BigDecimal gross,BigDecimal fee){
        this.id=UUID.randomUUID();this.order=order;this.sellerId=sellerId;this.storeId=storeId;this.gross=gross;this.platformFee=fee;
    }
    public BigDecimal getGross(){return gross;}
    public BigDecimal getPlatformFee(){return platformFee;}
    public List<OrderItem> getItems(){return items;}
}
