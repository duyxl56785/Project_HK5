package com.projecthk5.orderservice.ports;

import org.springframework.stereotype.Component;

/**
 * Hard OFF, even when TV4 has merged the Inventory pair credential filter.
 * Product→SKU/current Store data, OD-09 delegated actor/system provenance,
 * TV1 token management and positive service-pair integration are NOT approved.
 * Do NOT replace this with an HTTP caller merely by setting an environment token.
 */
@Component
public final class UnconfiguredInventoryOperationTransport implements InventoryOperationTransport {
    @Override
    public Reply send(InventoryOperationWire.Request request) {
        throw new NotSent("ORDER_INVENTORY_CALLER_DISABLED_PENDING_OD09_AND_SOURCE_AUTHORITY");
    }
}
