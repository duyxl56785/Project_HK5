package com.projecthk5.inventoryservice.api;

import com.projecthk5.inventoryservice.repository.InventoryOperationRepository;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** The Order pair token filter protects this read-only, committed RESERVE receipt. */
@RestController
class InventoryReserveReceiptController {
    private final InventoryOperationRepository operations;

    InventoryReserveReceiptController(InventoryOperationRepository operations) {
        this.operations = operations;
    }

    record Receipt(String operationId, String operationType, String fingerprint) { }

    @GetMapping("/internal/inventory/orders/{orderId}/reserve")
    ResponseEntity<Receipt> receipt(@PathVariable UUID orderId) {
        return ResponseEntity.of(operations.findById("order-" + orderId + "-reserve")
                .filter(row -> "RESERVE".equals(row.getOperationType()))
                .map(row -> new Receipt(row.getOperationId(), row.getOperationType(), row.getRequestHash())));
    }
}
