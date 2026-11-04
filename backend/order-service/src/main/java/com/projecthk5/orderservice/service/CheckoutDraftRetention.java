package com.projecthk5.orderservice.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * V5 retention (#152/#154): deletes only the shipping-PII draft of a SUCCEEDED attempt whose Order
 * is older than the retention window. A successful same-key replay reads the Order, never the draft.
 * Attempts, Orders, outbox and the Inventory journal are kept as idempotency/recovery evidence.
 * FAILED, PROCESSING, NEEDS_RECONCILIATION and any attempt with an unsettled journal row are never purged.
 */
@Component
@ConditionalOnProperty(name = "app.checkout.draft-purge-enabled", havingValue = "true")
public class CheckoutDraftRetention {
    private static final Logger log = LoggerFactory.getLogger(CheckoutDraftRetention.class);
    private final JdbcTemplate db;
    private final Duration retention;
    private final int batchSize;

    public CheckoutDraftRetention(JdbcTemplate db,
            @Value("${app.checkout.draft-retention:P7D}") Duration retention,
            @Value("${app.checkout.draft-purge-batch:500}") int batchSize) {
        if (retention.compareTo(Duration.ofDays(1)) < 0) throw new IllegalArgumentException("Unsafe draft retention");
        if (batchSize < 1 || batchSize > 5000) throw new IllegalArgumentException("Unsafe draft purge batch");
        this.db = db; this.retention = retention; this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.checkout.draft-purge-ms:3600000}",
        initialDelayString = "${app.checkout.draft-purge-ms:3600000}")
    public void purgeExpired() { purgeBefore(Instant.now().minus(retention)); }

    /** Each batch commits on its own so a long backlog never holds locks for the whole run. */
    public int purgeBefore(Instant deadline) {
        int total = 0, deleted;
        do {
            deleted = db.update("delete from checkout_order_drafts d using ("
                + "select d2.checkout_attempt_id from checkout_order_drafts d2 "
                + "join checkout_attempts a on a.id=d2.checkout_attempt_id "
                + "join orders o on o.id=a.order_id "
                + "where a.state='SUCCEEDED' and o.created_at < ? "
                + "and not exists (select 1 from inventory_operation_journal j where j.checkout_attempt_id=a.id "
                + "and j.state in ('PENDING','IN_FLIGHT','UNKNOWN')) "
                + "order by d2.checkout_attempt_id limit ? for update of d2, a skip locked) due "
                + "where d.checkout_attempt_id=due.checkout_attempt_id", Timestamp.from(deadline), batchSize);
            total += deleted;
        } while (deleted == batchSize);
        // Count only: draft content is shipping PII and never goes to logs.
        if (total > 0) log.info("Purged {} expired checkout drafts", total);
        return total;
    }
}
