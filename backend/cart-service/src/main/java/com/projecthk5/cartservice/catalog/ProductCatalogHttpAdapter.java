package com.projecthk5.cartservice.catalog;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Cart->Product pair: current Product/Store facts per add; store and seller never come from the client. */
@Component
public class ProductCatalogHttpAdapter implements CartCatalogPort {
    private final RestClient client;
    private final String token;

    public ProductCatalogHttpAdapter(@Value("${PRODUCT_INTERNAL_URL:http://localhost:8104}") String url,
                                     @Value("${CART_PRODUCT_TOKEN:}") String token) {
        this.client = com.projecthk5.security.JwtResourceServer.internalClient(url);
        this.token = token;
    }

    record Snapshot(String productId, String sellerId, String storeId, boolean available,
                    boolean sellerApproved, boolean storeOpen, BigDecimal unitPrice) {}

    @Override
    public ProductSnapshot resolve(String productId) {
        if (token.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product contract not connected");
        try {
            Snapshot s = client.get().uri("/internal/catalog/products/{id}", productId)
                    .header("X-Internal-Token", token).retrieve().body(Snapshot.class);
            if (s == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog unavailable");
            return new ProductSnapshot(s.productId(), s.storeId(), s.sellerId(),
                    s.available() && s.sellerApproved() && s.storeOpen(), s.unitPrice());
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog unavailable");
        }
    }
}
