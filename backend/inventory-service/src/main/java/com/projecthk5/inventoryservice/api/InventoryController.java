package com.projecthk5.inventoryservice.api;

import com.projecthk5.inventoryservice.domain.Stock;
import com.projecthk5.inventoryservice.service.InventoryOperationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/inventory")
public class InventoryController {
    private final InventoryOperationService service;

    public InventoryController(InventoryOperationService service) { this.service = service; }

    /** Development/bootstrap endpoint; production access control is not implemented yet. */
    @PutMapping("/{sku}/stock")
    public Stock setStock(@PathVariable String sku, @Valid @RequestBody StockRequest request) {
        return service.setAvailableStock(sku, request.availableQuantity());
    }

    @GetMapping("/{sku}/stock")
    public Stock getStock(@PathVariable String sku) { return service.getStock(sku); }

    @PostMapping("/reservations")
    public Stock reserve(@Valid @RequestBody InventoryOperationRequest request) { return service.reserve(request); }

    @PostMapping("/consumptions")
    public Stock consume(@Valid @RequestBody InventoryOperationRequest request) { return service.consume(request); }

    @PostMapping("/releases")
    public Stock release(@Valid @RequestBody InventoryOperationRequest request) { return service.release(request); }
}
