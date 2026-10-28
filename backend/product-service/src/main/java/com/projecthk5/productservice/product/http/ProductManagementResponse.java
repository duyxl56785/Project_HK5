package com.projecthk5.productservice.product.http;

import com.projecthk5.productservice.product.ProductPrice;
import com.projecthk5.productservice.product.ProductStatus;
import com.projecthk5.productservice.product.ProductOwnerPage;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter.StoredProduct;
import java.util.UUID;

/** Owner-management projection only. Not a public Catalog/wire contract or a replacement for authorization. */
public record ProductManagementResponse(UUID id, UUID storeId, UUID categoryId, String sku, String name,
                                        String unitPrice, String currency, ProductStatus status, String version) {
    public static ProductManagementResponse fromOwnerRow(ProductOwnerPage.Row row) {
        return new ProductManagementResponse(row.id(), row.storeId(), row.categoryId(), row.sku(), row.name(),
                ProductPrice.canonicalString(row.unitPrice(), row.currency()), row.currency(), row.status(), Long.toString(row.version()));
    }

    public static ProductManagementResponse from(StoredProduct stored) {
        if (stored == null || stored.listing() == null || stored.version() < 0) {
            throw new IllegalArgumentException("A complete authorized Product result is required");
        }
        var product = stored.listing();
        return new ProductManagementResponse(product.id(), product.storeId(), product.categoryId(), product.sku(),
                product.name(), ProductPrice.canonicalString(product.unitPrice(), product.currency()),
                product.currency(), product.status(), Long.toString(stored.version()));
    }
}
