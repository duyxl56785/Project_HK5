package com.projecthk5.orderservice.ports;

import java.math.BigDecimal;

public interface CatalogPort {
    ProductSnapshot verify(String productId);
    /** sku/name are immutable labels snapshotted onto the order; null only in legacy fixtures. */
    record ProductSnapshot(String productId, String sellerId,String storeId,boolean available,
                           boolean sellerApproved,boolean storeOpen,boolean houseStore,BigDecimal unitPrice,
                           String sku,String name, Boolean promotionApplied) {
        public ProductSnapshot(String productId, String sellerId, String storeId, boolean available,
                               boolean sellerApproved, boolean storeOpen, boolean houseStore, BigDecimal unitPrice,
                               String sku, String name) {
            this(productId, sellerId, storeId, available, sellerApproved, storeOpen, houseStore, unitPrice,
                    sku, name, null);
        }
        public ProductSnapshot(String productId, String sellerId,String storeId,boolean available,
                               boolean sellerApproved,boolean storeOpen,boolean houseStore,BigDecimal unitPrice) {
            this(productId,sellerId,storeId,available,sellerApproved,storeOpen,houseStore,unitPrice,null,null,null);
        }
    }
}
