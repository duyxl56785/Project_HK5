package com.projecthk5.productservice.web;

import com.projecthk5.productservice.catalog.CatalogBatchException;
import com.projecthk5.productservice.image.ImageStorageException;
import com.projecthk5.productservice.image.ImageValidationException;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.productservice.product.http.ProductManagementJson;
import com.projecthk5.productservice.product.persistence.ProductPersistenceException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps Product domain exceptions to the shared {code,message} error body. */
@RestControllerAdvice
public class ProductWebErrors {
    @ExceptionHandler(ProductManagementJson.InvalidInput.class)
    ResponseEntity<Map<String, String>> invalidJson(ProductManagementJson.InvalidInput e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request body");
    }

    @ExceptionHandler(ProductRuleException.class)
    ResponseEntity<Map<String, String>> rule(ProductRuleException e) {
        return switch (e.getCode()) {
            case INVALID_INPUT -> error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", e.getMessage());
            case INVALID_STATE -> error(HttpStatus.CONFLICT, "PRODUCT_INVALID_STATE", e.getMessage());
            case ACCESS_DENIED -> error(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", e.getMessage());
            case CATEGORY_NOT_FOUND -> error(HttpStatus.BAD_REQUEST, "CATEGORY_NOT_FOUND", e.getMessage());
            case AUTHORIZATION_UNAVAILABLE -> error(HttpStatus.SERVICE_UNAVAILABLE, "AUTHORIZATION_DEPENDENCY_UNAVAILABLE", e.getMessage());
        };
    }

    @ExceptionHandler(ProductPersistenceException.class)
    ResponseEntity<Map<String, String>> persistence(ProductPersistenceException e) {
        return switch (e.getCode()) {
            case DUPLICATE_SKU -> error(HttpStatus.CONFLICT, "PRODUCT_SKU_EXISTS", "SKU already exists");
            case CATEGORY_NOT_FOUND -> error(HttpStatus.BAD_REQUEST, "CATEGORY_NOT_FOUND", e.getMessage());
            case NOT_FOUND -> error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found");
            case STALE_VERSION -> error(HttpStatus.CONFLICT, "PRODUCT_VERSION_CONFLICT", e.getMessage());
            case IMMUTABLE_IDENTITY, CONSTRAINT_VIOLATION -> error(HttpStatus.CONFLICT, "PRODUCT_CONSTRAINT", e.getMessage());
            case UNAVAILABLE -> error(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_UNAVAILABLE", e.getMessage());
        };
    }

    @ExceptionHandler(CatalogBatchException.class)
    ResponseEntity<Map<String, String>> batch(CatalogBatchException e) {
        return e.getCode() == CatalogBatchException.Code.INVALID_REQUEST
                ? error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", e.getMessage())
                : error(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE", e.getMessage());
    }

    @ExceptionHandler(ImageValidationException.class)
    ResponseEntity<Map<String, String>> image(ImageValidationException e) {
        return error(HttpStatus.BAD_REQUEST, e.getCode().name(), e.getMessage());
    }

    @ExceptionHandler(ImageStorageException.class)
    ResponseEntity<Map<String, String>> storage(ImageStorageException e) {
        return e.getCode() == ImageStorageException.Code.NOT_FOUND
                ? error(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "Image not found")
                : error(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_STORAGE_UNAVAILABLE", "Image storage unavailable");
    }

    static ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message == null ? code : message));
    }
}
