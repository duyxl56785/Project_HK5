package com.projecthk5.productservice.product.persistence;

import com.projecthk5.productservice.product.ProductOwnerPage;
import com.projecthk5.productservice.product.ProductOwnerQuery;
import java.util.UUID;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Private persistence boundary; application policy must authorize Store before calling. */
public interface ProductOwnerSearch {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    ProductOwnerPage listOwned(UUID storeId, ProductOwnerQuery query);
}
