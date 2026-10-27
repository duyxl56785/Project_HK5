package com.projecthk5.productservice.catalog;

import com.projecthk5.productservice.product.ProductPrice;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** PRIVATE Product-only candidates, not an eligible public Catalog page or an approved wire DTO. */
public record CatalogCandidatePage(List<Candidate> candidates, long candidateTotal, int page, int pageSize) {
    public CatalogCandidatePage { candidates = List.copyOf(candidates); }

    public record Candidate(UUID productId, UUID storeId, UUID categoryId, String inventorySku,
                            String name, BigDecimal unitPrice, String currency) {
        public Candidate { ProductPrice.validate(unitPrice, currency); }
    }
}
