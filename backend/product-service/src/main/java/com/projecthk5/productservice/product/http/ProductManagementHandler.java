package com.projecthk5.productservice.product.http;

import com.projecthk5.productservice.product.ProductApplicationService;
import com.projecthk5.productservice.product.ProductRuleException;
import java.util.Objects;
import java.util.UUID;

/** Prepared management entry point. Deliberately not a Spring component, controller or authority implementation. */
public final class ProductManagementHandler {
    private final ProductManagementAuthority authority;
    private final ProductApplicationService application;
    private final ProductManagementJson json;

    public ProductManagementHandler(ProductManagementAuthority authority, ProductApplicationService application, ProductManagementJson json) {
        this.authority = Objects.requireNonNull(authority);
        this.application = Objects.requireNonNull(application);
        this.json = Objects.requireNonNull(json);
    }

    public ProductManagementResponse create(String payload) {
        String actor = actor();
        var input = json.create(payload);
        return ProductManagementResponse.from(application.create(actor, input.storeId(), input.categoryId(), input.sku(),
                input.name(), input.unitPrice(), input.currency()));
    }

    public ProductManagementResponse edit(UUID id, String payload) {
        String actor = actor();
        var input = json.edit(payload);
        return ProductManagementResponse.from(application.edit(actor, id, input.expectedVersion(), input.categoryId(), input.name(), input.unitPrice(), input.currency()));
    }

    public ProductManagementResponse submit(UUID id, String payload) {
        String actor = actor();
        return ProductManagementResponse.from(application.submit(actor, id, json.version(payload)));
    }

    public ProductManagementResponse approve(UUID id, String payload) {
        String actor = actor();
        return ProductManagementResponse.from(application.approve(actor, id, json.version(payload)));
    }

    public ProductManagementResponse reject(UUID id, String payload) {
        String actor = actor();
        var input = json.moderation(payload);
        return ProductManagementResponse.from(application.reject(actor, id, input.expectedVersion(), input.reason()));
    }

    public ProductManagementResponse suspend(UUID id, String payload) {
        String actor = actor();
        var input = json.moderation(payload);
        return ProductManagementResponse.from(application.suspend(actor, id, input.expectedVersion(), input.reason()));
    }

    public ProductManagementResponse deactivate(UUID id, String payload) {
        String actor = actor();
        return ProductManagementResponse.from(application.deactivate(actor, id, json.version(payload)));
    }

    public ProductManagementResponse findById(UUID id) {
        String actor = actor();
        return ProductManagementResponse.from(application.findById(actor, id));
    }

    private String actor() {
        String actor = authority.requireCurrentActor();
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 128) {
            throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, "Current verified Product authority is unavailable");
        }
        return actor; // Subject is exact; the application policy still checks roles/current ownership, not this string alone.
    }
}
