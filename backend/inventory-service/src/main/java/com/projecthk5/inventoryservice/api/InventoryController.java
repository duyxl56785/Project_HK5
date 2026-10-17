package com.projecthk5.inventoryservice.api;

import com.projecthk5.inventoryservice.model.InventoryItem;
import com.projecthk5.inventoryservice.repository.InventoryItemRepository;
import com.projecthk5.inventoryservice.service.InventoryOperationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryItemRepository items;
    private final InventoryOperationService operations;

    public InventoryController(InventoryItemRepository items, InventoryOperationService operations) {
        this.items = items;
        this.operations = operations;
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryBalanceResponse initialize(@Valid @RequestBody InitializeInventoryRequest request) {
        InventoryItem item = items.saveAndFlush(new InventoryItem(request.sku().trim(), request.availableQuantity()));
        return new InventoryBalanceResponse(item.getSku(), item.getAvailableQuantity(),
                item.getReservedQuantity(), item.getUpdatedAt());
    }

    @GetMapping("/{sku}")
    public InventoryBalanceResponse getBalance(@PathVariable String sku) {
        InventoryItem item = items.findBySku(sku)
                .orElseThrow(() -> new InventoryNotFoundException(sku));
        return new InventoryBalanceResponse(item.getSku(), item.getAvailableQuantity(),
                item.getReservedQuantity(), item.getUpdatedAt());
    }

    @PostMapping("/operations")
    public InventoryOperationResponse operate(@Valid @RequestBody InventoryOperationRequest request) {
        return operations.apply(request);
    }

}
