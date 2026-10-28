package com.projecthk5.productservice.product.http;

import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.productservice.product.persistence.ProductPersistenceException;
import org.springframework.transaction.CannotCreateTransactionException;

/** Prepared mapping only; not ControllerAdvice or an agreed public error contract. Never copy exception messages. */
public final class ProductManagementErrors {
    private ProductManagementErrors() { }
    public record Failure(int status, String code, String message) { }

    public static Failure from(RuntimeException failure) {
        if (failure instanceof ProductManagementJson.InvalidInput) return invalid();
        if (failure instanceof ProductRuleException rule) return switch (rule.getCode()) {
            case INVALID_INPUT -> invalid();
            case INVALID_STATE -> new Failure(409, "PRODUCT_INVALID_STATE", "Product transition is not allowed");
            case ACCESS_DENIED -> new Failure(403, "PRODUCT_ACCESS_DENIED", "Product access is denied");
            case CATEGORY_NOT_FOUND -> new Failure(404, "CATEGORY_NOT_FOUND", "Category was not found");
            case AUTHORIZATION_UNAVAILABLE -> unavailable();
        };
        if (failure instanceof ProductPersistenceException persistence) return switch (persistence.getCode()) {
            case NOT_FOUND -> new Failure(404, "PRODUCT_NOT_FOUND", "Product was not found");
            case CATEGORY_NOT_FOUND -> new Failure(404, "CATEGORY_NOT_FOUND", "Category was not found");
            case DUPLICATE_SKU -> new Failure(409, "PRODUCT_SKU_CONFLICT", "Product SKU is already assigned");
            case STALE_VERSION -> new Failure(409, "PRODUCT_VERSION_CONFLICT", "Reload Product before retrying");
            case IMMUTABLE_IDENTITY, CONSTRAINT_VIOLATION -> invalid();
            case UNAVAILABLE -> unavailable();
        };
        if (failure instanceof CannotCreateTransactionException) return unavailable();
        return new Failure(500, "PRODUCT_INTERNAL_ERROR", "Product operation could not be completed");
    }

    private static Failure invalid() { return new Failure(400, "PRODUCT_INVALID_INPUT", "Product input is invalid"); }
    private static Failure unavailable() { return new Failure(503, "PRODUCT_UNAVAILABLE", "Product operation is unavailable"); }
}
