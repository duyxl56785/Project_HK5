package com.projecthk5.orderservice.ports;

import java.math.BigDecimal;

public interface CatalogPort {
    ProductSnapshot verify(String productId);
    record ProductSnapshot(String productId, String sellerId,String storeId,boolean available,
                           boolean sellerApproved,boolean storeOpen,boolean houseStore,BigDecimal unitPrice) {}
}
