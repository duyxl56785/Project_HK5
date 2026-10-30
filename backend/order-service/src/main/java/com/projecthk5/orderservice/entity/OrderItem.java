package com.projecthk5.orderservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name="order_items")
public class OrderItem {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="seller_order_id",nullable=false) private SellerOrder sellerOrder;
    @Column(name="product_id",nullable=false,length=200) private String productId;
    @Column(nullable=false) private int quantity;
    @Column(name="unit_price_snapshot",nullable=false,precision=19,scale=2) private BigDecimal unitPriceSnapshot;
    @Column(name="line_total",nullable=false,precision=19,scale=2) private BigDecimal lineTotal;
    @Column(length=128) private String sku;
    @Column(name="product_name",length=255) private String productName;
    protected OrderItem() {}
    public OrderItem(SellerOrder so,String productId,int quantity,BigDecimal unitPrice,BigDecimal lineTotal){
        this.id=UUID.randomUUID();this.sellerOrder=so;this.productId=productId;this.quantity=quantity;this.unitPriceSnapshot=unitPrice;this.lineTotal=lineTotal;
    }
    public OrderItem(SellerOrder so,String productId,int quantity,BigDecimal unitPrice,BigDecimal lineTotal,String sku,String productName){
        this(so,productId,quantity,unitPrice,lineTotal);this.sku=sku;this.productName=productName;
    }
    public UUID getId(){return id;}
    public String getProductId(){return productId;}
    public int getQuantity(){return quantity;}
    public BigDecimal getUnitPriceSnapshot(){return unitPriceSnapshot;}
    public BigDecimal getLineTotal(){return lineTotal;}
    public String getSku(){return sku;}
    public String getProductName(){return productName;}
}
