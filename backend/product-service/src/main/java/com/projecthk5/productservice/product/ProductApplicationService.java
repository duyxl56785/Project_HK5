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
import org.springframework.transaction.annotation.Isolation;
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
        return change(actor, id, expectedVersion, true,
                (domain, current) -> domain.edit(actor, current, categoryId, name, price, currency));
    }

    public StoredProduct submit(String actor, UUID id, long expectedVersion) {
        return change(actor, id, expectedVersion, true, (domain, current) -> domain.submit(actor, current));
    }

    public StoredProduct approve(String actor, UUID id, long expectedVersion) {
        return change(actor, id, expectedVersion, false, (domain, current) -> domain.approve(actor, current));
    }

    public StoredProduct reject(String actor, UUID id, long expectedVersion, String reason) {
        return change(actor, id, expectedVersion, false, (domain, current) -> domain.reject(actor, current, reason));
    }

    public StoredProduct suspend(String actor, UUID id, long expectedVersion, String reason) {
        return change(actor, id, expectedVersion, false, (domain, current) -> domain.suspend(actor, current, reason));
    }

    public StoredProduct deactivate(String actor, UUID id, long expectedVersion) {
        return change(actor, id, expectedVersion, true, (domain, current) -> domain.deactivate(actor, current));
    }

    /** Owner management read. Public Catalog visibility is a separate contract. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public StoredProduct findById(String actor, UUID id) {
        ProductAccessPolicy policy = policy(actor);
        ProductLifecycleService domain = new ProductLifecycleService(policy, categories);
        StoredProduct current = currentOwned(policy, actor, id);
        domain.managementView(actor, current.listing());
        return current;
    }

    /** All lifecycle states, only within independently verified current management Store. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProductOwnerPage listOwned(String actor, ProductOwnerQuery query) {
        UUID storeId = managementStore(policy(actor), actor);
        if (query == null) throw invalid("Owner-list criteria are required");
        return persistence.listOwned(storeId, query);
    }

    private StoredProduct change(String actor, UUID id, long expectedVersion, boolean ownerManagement,
                                 BiFunction<ProductLifecycleService, ProductListing, ProductListing> transition) {
        if (expectedVersion < 0) throw invalid("A nonnegative expected version is required");
        ProductAccessPolicy policy = policy(actor);
        ProductLifecycleService domain = new ProductLifecycleService(policy, categories);
        StoredProduct current = ownerManagement ? currentOwned(policy, actor, id) : current(id);
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

    private StoredProduct currentOwned(ProductAccessPolicy policy, String actor, UUID id) {
        UUID storeId = managementStore(policy, actor);
        StoredProduct current = current(id);
        if (!storeId.equals(current.listing().storeId())) {
            throw new ProductPersistenceException(ProductPersistenceException.Code.NOT_FOUND, "Product was not found");
        }
        return current;
    }

    private UUID managementStore(ProductAccessPolicy policy, String actor) {
        // Verify current scope independently of the guessed Product ID, including when that ID is absent.
        UUID storeId = policy.currentManagementStore(actor);
        if (storeId == null) throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                "Verified current management Store is unavailable");
        var facts = policy.requireSellerManagement(actor, storeId);
        if (facts == null || !storeId.equals(facts.storeId())) {
            throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                    "Authoritative management Store could not be resolved");
        }
        if (!actor.equals(facts.ownerReference()) || !facts.sellerApproved() || !facts.storeOpen()) {
            throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "Store is not manageable by actor");
        }
        return storeId;
    }

    private ProductLifecycleService domain(String actor) {
        return new ProductLifecycleService(policy(actor), categories);
    }

    private ProductAccessPolicy policy(String actor) {
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 128) {
            throw invalid("Actor is required");
        }
        ProductAccessPolicy policy = policies.getIfUnique();
        if (policy == null) {
            throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                    "Verified Product authority is not configured");
        }
        return policy;
    }

    private static ProductRuleException invalid(String message) {
        return new ProductRuleException(ProductRuleException.Code.INVALID_INPUT, message);
    }
}
