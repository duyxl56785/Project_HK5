package com.projecthk5.orderservice.ports;

import com.projecthk5.security.InternalTokens;
import com.projecthk5.security.JwtResourceServer;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Order->Product pair: reads the current authoritative Product/Store snapshot, never a cache. */
@Component
public class CatalogHttpAdapter implements CatalogPort {
    private final RestClient product;
    private final String token;

    public CatalogHttpAdapter(@Value("${PRODUCT_INTERNAL_URL:http://localhost:8104}") String url,
                              @Value("${ORDER_PRODUCT_TOKEN:}") String token) {
        this.product = JwtResourceServer.internalClient(url);
        this.token = token;
    }

    record Wire(String productId, String sellerId, String storeId, String sku, String name, boolean available,
                boolean sellerApproved, boolean storeOpen, boolean houseStore, BigDecimal unitPrice) {}

    @Override
    public ProductSnapshot verify(String productId) {
        if (token.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product internal contract not configured");
        Wire w;
        try {
            w = product.get().uri("/internal/catalog/products/{id}", productId)
                    .header(InternalTokens.HEADER, token).retrieve().body(Wire.class);
        } catch (HttpClientErrorException.NotFound gone) {
            throw new IllegalStateException("Product no longer exists");
        } catch (HttpClientErrorException.BadRequest invalid) {
            throw new IllegalStateException("Invalid product reference");
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog unavailable", e);
        }
        if (w == null || w.productId() == null || w.unitPrice() == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog returned no snapshot");
        return new ProductSnapshot(w.productId(), w.sellerId(), w.storeId(), w.available(), w.sellerApproved(),
                w.storeOpen(), w.houseStore(), w.unitPrice(), w.sku(), w.name());
    }
}
