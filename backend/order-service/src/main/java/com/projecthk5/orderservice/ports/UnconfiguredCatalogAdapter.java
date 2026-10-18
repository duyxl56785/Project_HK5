package com.projecthk5.orderservice.ports;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UnconfiguredCatalogAdapter implements CatalogPort {
    @Override public ProductSnapshot verify(String productId) {
        // Deliberately fail closed: TV2 has not approved the Product/Store/Price API yet.
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Product/Store checkout contract not connected");
    }
}
