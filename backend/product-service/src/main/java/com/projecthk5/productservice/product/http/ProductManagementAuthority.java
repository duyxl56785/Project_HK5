package com.projecthk5.productservice.product.http;

/** Future verified current-account adapter. No implementation or Spring registration is supplied here. */
@FunctionalInterface
public interface ProductManagementAuthority {
    /**
     * Exact verified Account reference, never a body/header actor or a cached JWT role alone.
     * Must fail on missing/invalid/currently denied authority. ProductAccessPolicy still checks
     * current roles, ownership and Store/Seller facts for each application operation.
     */
    String requireCurrentActor();
}
