package com.projecthk5.productservice.web;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Initial stock of newly created Products. Inventory is outside the Product transaction, so this runs after every
 * row is stored and follows the transaction's outcome:
 * <ul>
 *   <li>A SKU that already has an Inventory row (left by an earlier failed attempt) is set even to 0, so a retry
 *       never inherits stale stock.</li>
 *   <li>If the transaction rolls back, every SKU touched here is put back to 0. Its Product does not exist, so
 *       nothing can sell it; a failed undo is logged and the next create of that SKU sets it again.</li>
 *   <li>An unknown outcome keeps the stock: the Products may have been committed.</li>
 * </ul>
 */
public final class InitialStock {
    private static final Logger log = LoggerFactory.getLogger(InitialStock.class);

    public record Line(String sku, long target, String operationId) {}

    private InitialStock() {}

    /** Must run inside the transaction that stored the Products. */
    public static void apply(DependencyClients inventory, List<Line> lines) {
        List<String> touched = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) undo(inventory, touched);
            }
        });
        for (Line line : lines) {
            if (line.target() == 0 && inventory.balance(line.sku()) == null) continue;
            touched.add(line.sku()); // before the call: Inventory may apply it and still fail to answer
            inventory.setAvailable(line.sku(), line.target(), line.operationId());
        }
    }

    private static void undo(DependencyClients inventory, List<String> skus) {
        for (String sku : skus) {
            try {
                inventory.setAvailable(sku, 0, "stock-undo-" + UUID.randomUUID());
            } catch (RuntimeException failed) {
                log.warn("Could not reset stock of SKU {} after a refused create; the next create of it resets it", sku, failed);
            }
        }
    }
}
