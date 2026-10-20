package com.projecthk5.orderservice.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marks crashed/stale in-flight intents UNKNOWN for observation. Does NOT perform
 * HTTP retry, automatically RELEASE, or assume that Inventory did not commit.
 */
@Component
public final class InventoryJournalReconciliationScanner {
    private final InventoryOperationJournal journal;
    private final Duration staleAfter;

    public InventoryJournalReconciliationScanner(InventoryOperationJournal journal,
            @Value("${app.inventory.journal-stale-seconds:600}") long staleSeconds) {
        if (staleSeconds < 120) throw new IllegalArgumentException("Unsafe journal stale interval");
        this.journal = journal;
        this.staleAfter = Duration.ofSeconds(staleSeconds);
    }

    @Scheduled(fixedDelayString = "${app.inventory.journal-scan-ms:60000}")
    public void markStale() {
        journal.flagStaleInFlight(staleAfter);
    }
}
