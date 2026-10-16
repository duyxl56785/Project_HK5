package com.projecthk5.inventoryservice.service;

import com.projecthk5.inventoryservice.api.InventoryOperationRequest;
import com.projecthk5.inventoryservice.domain.Stock;
import com.projecthk5.inventoryservice.error.InventoryException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * First-week inventory implementation. In-memory only: restart loses state.
 * Replace the maps with a transactional database repository before integration.
 */
@Service
public class InventoryOperationService {
    private final Map<String, Stock> stocks = new HashMap<>();
    private final Map<String, OperationRecord> operations = new HashMap<>();

    public synchronized Stock setAvailableStock(String sku, int availableQuantity) {
        requireSku(sku);
        if (availableQuantity < 0) throw badRequest("INVALID_QUANTITY", "availableQuantity must be >= 0");
        Stock current = stocks.get(sku);
        if (current != null && current.reservedQuantity() > 0) {
            throw conflict("STOCK_HAS_RESERVATIONS", "Cannot replace stock while quantity is reserved");
        }
        Stock updated = new Stock(sku, availableQuantity, 0);
        stocks.put(sku, updated);
        return updated;
    }

    public synchronized Stock getStock(String sku) {
        requireSku(sku);
        Stock stock = stocks.get(sku);
        if (stock == null) throw notFound("SKU_NOT_FOUND", "No stock record for SKU " + sku);
        return stock;
    }

    public synchronized Stock reserve(InventoryOperationRequest request) {
        return execute("RESERVE", request, () -> {
            Stock stock = requireStock(request.sku());
            if (stock.availableQuantity() < request.quantity()) {
                throw conflict("INSUFFICIENT_STOCK", "Not enough available quantity for SKU " + request.sku());
            }
            Stock updated = new Stock(stock.sku(), stock.availableQuantity() - request.quantity(),
                    stock.reservedQuantity() + request.quantity());
            stocks.put(updated.sku(), updated);
            return updated;
        });
    }

    public synchronized Stock consume(InventoryOperationRequest request) {
        return execute("CONSUME", request, () -> {
            Stock stock = requireStock(request.sku());
            if (stock.reservedQuantity() < request.quantity()) {
                throw conflict("INSUFFICIENT_RESERVED_STOCK", "Not enough reserved quantity to consume for SKU " + request.sku());
            }
            Stock updated = new Stock(stock.sku(), stock.availableQuantity(),
                    stock.reservedQuantity() - request.quantity());
            stocks.put(updated.sku(), updated);
            return updated;
        });
    }

    public synchronized Stock release(InventoryOperationRequest request) {
        return execute("RELEASE", request, () -> {
            Stock stock = requireStock(request.sku());
            if (stock.reservedQuantity() < request.quantity()) {
                throw conflict("INSUFFICIENT_RESERVED_STOCK", "Not enough reserved quantity to release for SKU " + request.sku());
            }
            Stock updated = new Stock(stock.sku(), stock.availableQuantity() + request.quantity(),
                    stock.reservedQuantity() - request.quantity());
            stocks.put(updated.sku(), updated);
            return updated;
        });
    }

    private Stock execute(String action, InventoryOperationRequest request, StockSupplier actionBody) {
        validate(request);
        String key = action + ":" + request.operationId();
        String fingerprint = request.sku() + ":" + request.quantity();
        OperationRecord previous = operations.get(key);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) {
                throw conflict("IDEMPOTENCY_KEY_REUSED", "operationId was already used with a different SKU or quantity");
            }
            return previous.result();
        }
        Stock result = actionBody.get();
        operations.put(key, new OperationRecord(fingerprint, result));
        return result;
    }

    private Stock requireStock(String sku) {
        Stock stock = stocks.get(sku);
        if (stock == null) throw notFound("SKU_NOT_FOUND", "No stock record for SKU " + sku);
        return stock;
    }

    private static void validate(InventoryOperationRequest request) {
        if (request == null || request.operationId() == null || request.operationId().isBlank())
            throw badRequest("INVALID_OPERATION_ID", "operationId must not be blank");
        requireSku(request.sku());
        if (request.quantity() < 1) throw badRequest("INVALID_QUANTITY", "quantity must be >= 1");
    }

    private static void requireSku(String sku) {
        if (sku == null || sku.isBlank()) throw badRequest("INVALID_SKU", "sku must not be blank");
    }

    private static InventoryException badRequest(String code, String message) {
        return new InventoryException(HttpStatus.BAD_REQUEST, code, message);
    }
    private static InventoryException notFound(String code, String message) {
        return new InventoryException(HttpStatus.NOT_FOUND, code, message);
    }
    private static InventoryException conflict(String code, String message) {
        return new InventoryException(HttpStatus.CONFLICT, code, message);
    }

    @FunctionalInterface private interface StockSupplier { Stock get(); }
    private record OperationRecord(String fingerprint, Stock result) {}
}
