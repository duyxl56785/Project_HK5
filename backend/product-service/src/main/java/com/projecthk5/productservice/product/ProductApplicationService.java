package com.projecthk5.productservice.product;

import com.projecthk5.productservice.category.CategoryRepository;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter.StoredProduct;
import com.projecthk5.productservice.product.persistence.ProductPersistenceException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal orchestration; a verified ProductAccessPolicy is required before any resource lookup or write. */
@Service
@Transactional(propagation = Propagation.REQUIRED)
public class ProductApplicationService {
    private final ProductPersistenceAdapter persistence;
    private final CategoryRepository categories;
    private final ObjectProvider<ProductAccessPolicy> policies;

    public ProductApplicationService(ProductPersistenceAdapter persistence, CategoryRepository categories,
                                     ObjectProvider<ProductAccessPolicy> policies) {
        this.persistence = persistence;
        this.categories = categories;
        this.policies = policies;
    }

    public StoredProduct create(String actor, UUID storeId, UUID categoryId, String sku, String name,
                                BigDecimal price, String currency) {
        ProductLifecycleService domain = domain(actor);
        return persistence.create(domain.create(actor, storeId, categoryId, sku, name, price, currency));
    }

    public StoredProduct edit(String actor, UUID id, long expectedVersion, UUID categoryId, String name,
                              BigDecimal price, String currency) {
        return change(actor, id, expectedVersion,
                (domain, current) -> domain.edit(actor, current, categoryId, name, price, currency));
    }

    public StoredProduct submit(String actor, UUID id, long expectedVersion) {
        return change(actor, id, expectedVersion, (domain, current) -> domain.submit(actor, current));
    }

    public StoredProduct approve(String actor, UUID id, long expectedVersion) {
        return change(actor, id, expectedVersion, (domain, current) -> domain.approve(actor, current));
    }

    public StoredProduct reject(String actor, UUID id, long expectedVersion, String reason) {
        return change(actor, id, expectedVersion, (domain, current) -> domain.reject(actor, current, reason));
    }

    public StoredProduct suspend(String actor, UUID id, long expectedVersion, String reason) {
        return change(actor, id, expectedVersion, (domain, current) -> domain.suspend(actor, current, reason));
    }

    public StoredProduct deactivate(String actor, UUID id, long expectedVersion) {
        return change(actor, id, expectedVersion, (domain, current) -> domain.deactivate(actor, current));
    }

    /** Owner management read. Public Catalog visibility is a separate contract. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public StoredProduct findById(String actor, UUID id) {
        ProductLifecycleService domain = domain(actor);
        StoredProduct current = current(id);
        domain.managementView(actor, current.listing());
        return current;
    }

    private StoredProduct change(String actor, UUID id, long expectedVersion,
                                 BiFunction<ProductLifecycleService, ProductListing, ProductListing> transition) {
        if (expectedVersion < 0) throw invalid("A nonnegative expected version is required");
        ProductLifecycleService domain = domain(actor);
        StoredProduct current = current(id);
        ProductListing next = transition.apply(domain, current.listing());
        // Tie the transition to the snapshot used for authorization/state checks, not a later refreshed version.
        if (current.version() != expectedVersion) {
            throw new ProductPersistenceException(ProductPersistenceException.Code.STALE_VERSION,
                    "Product changed since it was read");
        }
        return persistence.update(next, expectedVersion);
    }

    private StoredProduct current(UUID id) {
        if (id == null) throw invalid("Product ID is required");
        return persistence.findFreshById(id).orElseThrow(() ->
                new ProductPersistenceException(ProductPersistenceException.Code.NOT_FOUND, "Product was not found"));
    }

    private ProductLifecycleService domain(String actor) {
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 128) {
            throw invalid("Actor is required");
        }
        ProductAccessPolicy policy = policies.getIfUnique();
        if (policy == null) {
            throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                    "Verified Product authority is not configured");
        }
        return new ProductLifecycleService(policy, categories);
    }

    private static ProductRuleException invalid(String message) {
        return new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, message);
    }
}
