package com.projecthk5.productservice.web;

import com.projecthk5.security.ApiException;
import com.projecthk5.security.JwtResourceServer;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Product's outbound pair call for stock management in Inventory. */
@Component
public class DependencyClients {
    private final RestClient inventory;
    private final String inventoryToken;

    public DependencyClients(@Value("${INVENTORY_INTERNAL_URL:http://localhost:8105}") String inventoryUrl,
                             @Value("${PRODUCT_INVENTORY_TOKEN:}") String inventoryToken) {
        this.inventory = JwtResourceServer.internalClient(inventoryUrl);
        this.inventoryToken = inventoryToken;
    }

    public record Balance(String sku, long availableQuantity, long reservedQuantity) {}

    /** Empty when Inventory has no row for the SKU yet. */
    public Balance balance(String sku) {
        try {
            return inventory.get().uri("/api/inventory/{sku}", sku).header("X-Inventory-Internal-Token", inventoryToken)
                    .retrieve().body(Balance.class);
        } catch (HttpClientErrorException.NotFound none) {
            return null;
        } catch (RestClientException e) {
            throw ApiException.unavailable("INVENTORY_UNAVAILABLE", "Inventory unavailable");
        }
    }

    /** Sets on-hand availability by creating the row or applying an INCREASE/DECREASE delta. */
    public Balance setAvailable(String sku, long target, String operationId) {
        Balance current = balance(sku);
        try {
            if (current == null) {
                return inventory.post().uri("/api/inventory/items").header("X-Inventory-Internal-Token", inventoryToken)
                        .body(Map.of("sku", sku, "availableQuantity", target)).retrieve().body(Balance.class);
            }
            long delta = target - current.availableQuantity();
            if (delta == 0) return current;
            inventory.post().uri("/api/inventory/operations").header("X-Inventory-Internal-Token", inventoryToken)
                    .body(Map.of("operationId", operationId, "type", delta > 0 ? "INCREASE" : "DECREASE",
                            "items", List.of(Map.of("sku", sku, "quantity", Math.abs(delta)))))
                    .retrieve().toBodilessEntity();
            return balance(sku);
        } catch (HttpClientErrorException e) {
            throw ApiException.conflict("INVENTORY_REJECTED", "Inventory rejected the stock change");
        } catch (RestClientException e) {
            throw ApiException.unavailable("INVENTORY_UNAVAILABLE", "Inventory unavailable");
        }
    }
}
