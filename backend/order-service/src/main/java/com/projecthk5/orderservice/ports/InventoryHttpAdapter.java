package com.projecthk5.orderservice.ports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.security.InternalTokens;
import com.projecthk5.security.JwtResourceServer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Order->Inventory pair (X-Internal-Token, RESERVE/CONSUME/RELEASE only). Operation ids are
 * deterministic per order/store order, so a retry after an unknown outcome is replayed, not re-applied.
 */
@Component
public class InventoryHttpAdapter implements InventoryPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RestClient inventory;
    private final String token;

    public InventoryHttpAdapter(@Value("${INVENTORY_INTERNAL_URL:http://localhost:8105}") String url,
                                @Value("${ORDER_INVENTORY_TOKEN:}") String token) {
        this.inventory = JwtResourceServer.internalClient(url);
        this.token = token;
    }

    public static String reserveId(UUID orderId) { return "order-" + orderId + "-reserve"; }

    @Override
    public void reserve(UUID orderId, List<Reservation> items) {
        apply(reserveId(orderId), "RESERVE", items);
    }

    @Override
    public void release(UUID orderId) {
        // Whole-order release needs the item list; callers use apply(...) with the stored lines.
        throw new UnsupportedOperationException("Use apply(operationId, RELEASE, lines)");
    }

    @Override
    public void apply(String operationId, String type, List<Reservation> items) {
        if (token.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory internal contract not configured");
        if (items.stream().anyMatch(i -> i.sku() == null || i.sku().isBlank()))
            throw new IllegalArgumentException("Inventory lines require a SKU");
        List<Map<String, Object>> lines = items.stream()
                .map(i -> Map.<String, Object>of("sku", i.sku(), "quantity", i.quantity())).toList();
        try {
            inventory.post().uri("/api/inventory/operations").header(InternalTokens.HEADER, token)
                    .body(Map.of("operationId", operationId, "type", type, "items", lines))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.Conflict | HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest definitive) {
            // Inventory applies each operation atomically: a 4xx means nothing moved.
            String code = code(definitive);
            throw new Rejected(code, "INSUFFICIENT_STOCK".equals(code)
                    ? "Not enough stock for " + sku(definitive) : "Inventory rejected " + type + ": " + code);
        } catch (RestClientException unknown) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory unavailable", unknown);
        }
    }

    private static String code(HttpClientErrorException e) {
        try {
            JsonNode body = JSON.readTree(e.getResponseBodyAsString());
            return body.path("code").asText("INVENTORY_REJECTED");
        } catch (Exception unreadable) {
            return "INVENTORY_REJECTED";
        }
    }

    private static String sku(HttpClientErrorException e) {
        try {
            return JSON.readTree(e.getResponseBodyAsString()).path("sku").asText("an item");
        } catch (Exception unreadable) {
            return "an item";
        }
    }
}
