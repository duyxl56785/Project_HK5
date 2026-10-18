
package com.projecthk5.cartservice.catalog;

import java.math.BigDecimal;

public interface CartCatalogPort {

    ProductSnapshot resolve(String productId);

    record ProductSnapshot(
        String productId,
        String storeId,
        String sellerId,
        boolean available,
        BigDecimal unitPrice
    ) {}
}
