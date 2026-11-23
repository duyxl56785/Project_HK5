package com.projecthk5.productservice.web;

import com.projecthk5.productservice.product.ProductStatus;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter.StoredProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;

/** Reads variant groups and carries a lead's lifecycle step to the variants that were in the same state. */
@Component
public class VariantGroups {
    private final VariantRepository variants;
    private final ProductPersistenceAdapter persistence;

    public VariantGroups(VariantRepository variants, ProductPersistenceAdapter persistence) {
        this.variants = variants;
        this.persistence = persistence;
    }

    public Optional<ProductExtras.Variant> of(UUID productId) {
        return variants.findById(productId);
    }

    public List<ProductExtras.Variant> group(UUID groupId) {
        return variants.findByGroupIdOrderByPosition(groupId);
    }

    /** Current state, read before a lifecycle step so the step can be repeated on the variants that match it. */
    public ProductStatus statusOf(UUID productId) {
        return persistence.findFreshById(productId).map(p -> p.listing().status()).orElse(null);
    }

    /**
     * Repeats the lead's step on each other variant still in the state the lead had before it. A variant stopped
     * or moderated on its own keeps its state. Callers run this in the lead's transaction so the group moves together.
     */
    public void follow(UUID leadId, ProductStatus before, BiFunction<UUID, Long, StoredProduct> step) {
        if (before == null || of(leadId).filter(ProductExtras.Variant::isLead).isEmpty()) return;
        for (ProductExtras.Variant variant : group(leadId)) {
            if (variant.isLead()) continue;
            StoredProduct current = persistence.findFreshById(variant.getProductId()).orElse(null);
            if (current != null && current.listing().status() == before) step.apply(variant.getProductId(), current.version());
        }
    }

    public StoredProduct current(UUID productId) {
        return persistence.findFreshById(productId).orElseThrow();
    }
}
