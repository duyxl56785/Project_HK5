package com.projecthk5.productservice.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Authorized local owner snapshot, not an HTTP contract or public Catalog page. */
public record ProductOwnerPage(List<Row> products, long total, int page, int pageSize) {
    public ProductOwnerPage { products = List.copyOf(products); }

    public record Row(UUID id, UUID storeId, UUID categoryId, String sku, String name,
                      BigDecimal unitPrice, String currency, ProductStatus status, long version) {
        public Row {
            ProductPrice.validate(unitPrice, currency);
            if (version < 0) throw new IllegalArgumentException("A nonnegative Product version is required");
        }
    }
}
