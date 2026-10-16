package com.projecthk5.cartservice.entity;

import jakarta.persistence.*;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(name="uq_cart_product",columnNames={"cart_id","product_id"}))
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;
    @Column(name="product_id", nullable=false, length=200)
    private String productId;
    @Column(name="store_id", nullable=false, length=200)
    private String storeId;
    @Column(name="seller_id", nullable=false, length=200)
    private String sellerId;
    @Column(nullable=false) private int quantity;
    @Column(name="unit_price_snapshot",nullable=false,precision=19,scale=2)
    private BigDecimal unitPriceSnapshot;
    protected CartItem() {}
    public CartItem(Cart cart,String productId,String storeId,String sellerId,int quantity,BigDecimal unitPriceSnapshot) {
        this.cart=cart;this.productId=productId;this.storeId=storeId;this.sellerId=sellerId;this.quantity=quantity;this.unitPriceSnapshot=unitPriceSnapshot;
    }
    public String getProductId(){return productId;}
    public String getStoreId(){return storeId;}
    public String getSellerId(){return sellerId;}
    public BigDecimal getUnitPriceSnapshot(){return unitPriceSnapshot;}
    public int getQuantity(){return quantity;}
    public void setQuantity(int quantity){this.quantity=quantity;}
}
