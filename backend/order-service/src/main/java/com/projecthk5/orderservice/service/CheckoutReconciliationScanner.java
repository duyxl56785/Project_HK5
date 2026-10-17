package com.projecthk5.orderservice.service;

import com.projecthk5.orderservice.entity.CheckoutAttempt;
import com.projecthk5.orderservice.repository.CheckoutAttemptRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable safety scan: flags abandoned attempts for operator-led reconciliation.
 * Never guesses whether Inventory or DB committed; never releases reservations.
 * Production traffic stays gated until Inventory reconciliation contract is approved.
 */
@Component
public class CheckoutReconciliationScanner {
    private final CheckoutAttemptRepository attempts;
    private final long staleSeconds;
    public CheckoutReconciliationScanner(CheckoutAttemptRepository attempts,
            @Value("${app.checkout.stale-seconds:600}") long staleSeconds) {
        if (staleSeconds < 120) throw new IllegalArgumentException("Unsafe checkout stale interval");
        this.attempts=attempts;this.staleSeconds=staleSeconds;
    }
    @Scheduled(fixedDelayString="${app.checkout.reconcile-scan-ms:60000}")
    @Transactional
    public void flagAbandonedAttempts() {
        // Predicate and transition are one DB operation. A writer committing SUCCEEDED
        // while this UPDATE waits on its row lock must not be overwritten by a stale entity.
        // No remote compensation or mutation of a committed order/reservation is attempted.
        attempts.flagStaleProcessing(CheckoutAttempt.State.PROCESSING,
            CheckoutAttempt.State.NEEDS_RECONCILIATION, Instant.now().minusSeconds(staleSeconds));
    }
}
