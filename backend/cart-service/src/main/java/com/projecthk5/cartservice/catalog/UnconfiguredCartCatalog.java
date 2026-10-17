package com.projecthk5.cartservice.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UnconfiguredCartCatalog implements CartCatalogPort {
    @Override public ProductSnapshot resolve(String productId) {
        // Must be replaced by TV2 approved product catalog adapter; NEVER trust seller/store from client.
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product contract not connected");
    }
}
