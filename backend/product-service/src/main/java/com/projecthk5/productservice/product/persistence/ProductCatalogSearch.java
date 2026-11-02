package com.projecthk5.productservice.product.persistence;

import com.projecthk5.productservice.catalog.CatalogCandidatePage;
import com.projecthk5.productservice.catalog.CatalogSearchQuery;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

public interface ProductCatalogSearch {
    /** Local Product snapshot only; callers must never publish this as a Seller/Store-eligible page. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    CatalogCandidatePage searchCandidates(CatalogSearchQuery query);
}
