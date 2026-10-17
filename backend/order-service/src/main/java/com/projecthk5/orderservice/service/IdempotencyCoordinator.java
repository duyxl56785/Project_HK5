package com.projecthk5.orderservice.service;

import com.projecthk5.orderservice.entity.CheckoutAttempt;
import com.projecthk5.orderservice.repository.CheckoutAttemptRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdempotencyCoordinator {
    private final CheckoutAttemptRepository repo;
    public IdempotencyCoordinator(CheckoutAttemptRepository repo){this.repo=repo;}
    @Transactional
    public BeginResult begin(String customerId,String key,String fingerprint){
        var existing=repo.findByCustomerIdAndIdempotencyKey(customerId,key);
        if(existing.isPresent()){
            var old=existing.get();
            if (!old.getFingerprint().equals(fingerprint)) throw new ResponseStatusException(HttpStatus.CONFLICT,"IDEMPOTENCY_PAYLOAD_MISMATCH");
            return new BeginResult(old, false);
        }
        return new BeginResult(repo.saveAndFlush(new CheckoutAttempt(customerId,key,fingerprint)), true);
    }
    public record BeginResult(CheckoutAttempt attempt, boolean created) {}
    @Transactional
    public void fail(java.util.UUID attemptId,boolean uncertain){
        // An uncertain response must not overwrite SUCCEEDED if the writer won the race.
        int changed=repo.failIfProcessing(attemptId, CheckoutAttempt.State.PROCESSING,
            uncertain ? CheckoutAttempt.State.NEEDS_RECONCILIATION : CheckoutAttempt.State.FAILED);
        if (changed==0 && !repo.existsById(attemptId)) throw new java.util.NoSuchElementException();
    }
}
