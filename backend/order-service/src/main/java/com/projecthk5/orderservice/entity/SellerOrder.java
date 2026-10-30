package com.projecthk5.orderservice.entity;

import com.projecthk5.orderservice.finance.FinanceRules;
import com.projecthk5.orderservice.fulfillment.SellerOrderLifecycle;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="seller_orders")
public class SellerOrder {
    public enum CodStatus { PENDING, COLLECTED, VOID }

    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private Order order;
    @Column(name="seller_id",nullable=false,length=200) private String sellerId;
    @Column(name="store_id",nullable=false,length=200) private String storeId;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal gross;
    @Column(name="platform_fee",nullable=false,precision=19,scale=2) private BigDecimal platformFee;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private SellerOrderLifecycle.Status status=SellerOrderLifecycle.Status.PLACED;
    @Enumerated(EnumType.STRING) @Column(name="cod_status",nullable=false,length=16) private CodStatus codStatus=CodStatus.PENDING;
    @Column(name="status_reason",length=500) private String statusReason;
    @Column(name="delivered_at") private Instant deliveredAt;
    @Column(name="cod_collected_at") private Instant codCollectedAt;
    @Column(name="cod_collected_by",length=200) private String codCollectedBy;
    @Column(name="settled_at") private Instant settledAt;
    @Column(name="settled_by",length=200) private String settledBy;
    @Column(name="updated_at",nullable=false) private Instant updatedAt=Instant.now();
    @Version @Column(nullable=false) private long version;
    @OneToMany(mappedBy="sellerOrder",cascade=CascadeType.ALL,orphanRemoval=true)
    private List<OrderItem> items=new ArrayList<>();
    protected SellerOrder() {}
    public SellerOrder(Order order,String sellerId,String storeId,BigDecimal gross,BigDecimal fee){
        this.id=UUID.randomUUID();this.order=order;this.sellerId=sellerId;this.storeId=storeId;this.gross=gross;this.platformFee=fee;
    }
    public UUID getId(){return id;}
    public Order getOrder(){return order;}
    public String getSellerId(){return sellerId;}
    public String getStoreId(){return storeId;}
    public BigDecimal getGross(){return gross;}
    public BigDecimal getPlatformFee(){return platformFee;}
    public BigDecimal getSellerNet(){return gross.subtract(platformFee);}
    public SellerOrderLifecycle.Status getStatus(){return status;}
    public CodStatus getCodStatus(){return codStatus;}
    public String getStatusReason(){return statusReason;}
    public Instant getDeliveredAt(){return deliveredAt;}
    public Instant getCodCollectedAt(){return codCollectedAt;}
    public Instant getSettledAt(){return settledAt;}
    public Instant getUpdatedAt(){return updatedAt;}
    public long getVersion(){return version;}
    public List<OrderItem> getItems(){return items;}

    public boolean isClosed(){
        return status==SellerOrderLifecycle.Status.CANCELLED || status==SellerOrderLifecycle.Status.REJECTED;
    }

    /** Applies a validated lifecycle step; closing a Store order voids its COD collection. */
    public SellerOrderLifecycle.Status apply(SellerOrderLifecycle.Action action,String reason,Instant now){
        SellerOrderLifecycle.Status next=SellerOrderLifecycle.transition(status,action,reason);
        this.status=next;
        this.updatedAt=now;
        if (action==SellerOrderLifecycle.Action.REJECT || action==SellerOrderLifecycle.Action.CANCEL) {
            this.statusReason=reason==null||reason.isBlank()?null:reason.strip();
            this.codStatus=CodStatus.VOID;
        }
        if (next==SellerOrderLifecycle.Status.DELIVERED) this.deliveredAt=now;
        return next;
    }

    /** Platform confirms the carrier remitted the cash: allowed once, only after delivery. */
    public void collectCod(String adminId,Instant now){
        if (status!=SellerOrderLifecycle.Status.DELIVERED)
            throw new IllegalStateException("COD can only be collected after delivery");
        if (codStatus!=CodStatus.PENDING)
            throw new IllegalStateException("COD already "+codStatus);
        this.codStatus=CodStatus.COLLECTED;this.codCollectedAt=now;this.codCollectedBy=adminId;this.updatedAt=now;
    }

    /** Pays out sellerNet; FinanceRules guards DELIVERED + COD collected + not settled twice. */
    public void settle(String adminId,Instant now){
        FinanceRules.requireSettleable(status==SellerOrderLifecycle.Status.DELIVERED,
            codStatus==CodStatus.COLLECTED,settledAt!=null);
        this.settledAt=now;this.settledBy=adminId;this.updatedAt=now;
    }
}
