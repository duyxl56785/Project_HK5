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
        for (CheckoutAttempt attempt : attempts.findByStateAndCreatedAtBefore(
                CheckoutAttempt.State.PROCESSING,Instant.now().minusSeconds(staleSeconds))) {
            if (attempt.getState()==CheckoutAttempt.State.PROCESSING)
                attempt.fail(true); // NEVER compensate on timeout/crash.
        }
    }
}
