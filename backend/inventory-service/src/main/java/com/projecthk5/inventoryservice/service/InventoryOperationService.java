package com.projecthk5.inventoryservice.service;

import com.projecthk5.inventoryservice.api.*;
import com.projecthk5.inventoryservice.model.InventoryItem;
import com.projecthk5.inventoryservice.repository.InventoryItemRepository;
import com.projecthk5.inventoryservice.repository.InventoryOperationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class InventoryOperationService {
    private final InventoryItemRepository items;
    private final InventoryOperationRepository operations;

    public InventoryOperationService(InventoryItemRepository items, InventoryOperationRepository operations) {
        this.items = items;
        this.operations = operations;
    }

    @Transactional
    public InventoryOperationResponse apply(InventoryOperationRequest request) {
        Map<String, Long> quantities = canonicalItems(request);
        List<InventoryItem> locked = items.findAllBySkuForUpdate(quantities.keySet());
        if (locked.size() != quantities.size()) {
            List<String> missing = quantities.keySet().stream()
                    .filter(sku -> locked.stream().noneMatch(item -> item.getSku().equals(sku)))
                    .toList();
            throw new InventoryOperationServiceException("INVENTORY_ITEM_NOT_FOUND",
                    "Inventory items not found: " + String.join(", ", missing));
        }

        String hash = requestHash(request.type(), quantities);
        var prior = operations.findById(request.operationId());
        if (prior.isPresent()) {
            return replayOrReject(request, quantities, hash, locked, prior.get().getRequestHash());
        }

        int claimed = operations.claimOperationId(request.operationId(), request.type().name(), hash);
        if (claimed == 0) {
            var concurrent = operations.findById(request.operationId());
            if (concurrent.isEmpty()) {
                throw new InventoryOperationServiceException("IDEMPOTENCY_CLAIM_CONFLICT",
                        "operationId could not be claimed; retry the operation");
            }
            return replayOrReject(request, quantities, hash, locked, concurrent.get().getRequestHash());
        }

        Map<String, InventoryItem> bySku = locked.stream()
                .collect(Collectors.toMap(InventoryItem::getSku, item -> item));
        for (Map.Entry<String, Long> entry : quantities.entrySet()) {
            InventoryItem item = bySku.get(entry.getKey());
            long quantity = entry.getValue();
            switch (request.type()) {
                case INCREASE -> item.increaseAvailable(quantity);
                case DECREASE -> item.decreaseAvailable(quantity);
                case RESERVE -> item.reserve(quantity);
                case CONSUME -> item.consumeReserved(quantity);
                case RELEASE -> item.releaseReserved(quantity);
            }
        }

        items.flush();
        return response(request.operationId(), request.type(), false, locked);
    }

    private static InventoryOperationResponse replayOrReject(InventoryOperationRequest request,
                                                               Map<String, Long> quantities,
                                                               String hash,
                                                               List<InventoryItem> locked,
                                                               String priorHash) {
        if (!priorHash.equals(hash)) {
            throw new InventoryOperationServiceException("IDEMPOTENCY_KEY_REUSED",
                    "operationId was already used with a different request");
        }
        return response(request.operationId(), request.type(), true, locked);
    }

    private static Map<String, Long> canonicalItems(InventoryOperationRequest request) {
        if (request.operationId().length() > 128) {
            throw new InventoryOperationServiceException("INVALID_OPERATION_ID", "operationId must be at most 128 characters");
        }
        TreeMap<String, Long> sorted = new TreeMap<>();
        for (InventoryOperationRequest.ItemQuantity item : request.items()) {
            String sku = item.sku().trim();
            if (sku.isEmpty() || sku.length() > 128) {
                throw new InventoryOperationServiceException("INVALID_SKU", "sku must be between 1 and 128 characters");
            }
            if (item.quantity() <= 0) {
                throw new InventoryOperationServiceException("INVALID_QUANTITY", "quantity must be greater than zero");
            }
            if (sorted.putIfAbsent(sku, item.quantity()) != null) {
                throw new InventoryOperationServiceException("DUPLICATE_SKU", "Each SKU may appear only once per operation");
            }
        }
        return sorted;
    }

    private static String requestHash(InventoryOperationType type, Map<String, Long> quantities) {
        StringBuilder canonical = new StringBuilder();
        appendLengthPrefixed(canonical, type.name());
        appendLengthPrefixed(canonical, Integer.toString(quantities.size()));
        quantities.forEach((sku, quantity) -> {
            appendLengthPrefixed(canonical, sku);
            appendLengthPrefixed(canonical, Long.toString(quantity));
        });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private static void appendLengthPrefixed(StringBuilder canonical, String value) {
        canonical.append(value.length()).append(':').append(value);
    }

    private static InventoryOperationResponse response(String operationId, InventoryOperationType type,
                                                       boolean replayed, List<InventoryItem> items) {
        List<InventoryBalanceResponse> balances = items.stream()
                .map(item -> new InventoryBalanceResponse(item.getSku(), item.getAvailableQuantity(),
                        item.getReservedQuantity(), item.getUpdatedAt()))
                .toList();
        return new InventoryOperationResponse(operationId, type, replayed, balances);
    }
}
