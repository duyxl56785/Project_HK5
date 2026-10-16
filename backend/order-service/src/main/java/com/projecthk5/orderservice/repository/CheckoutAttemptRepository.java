package com.projecthk5.orderservice.repository;

import com.projecthk5.orderservice.entity.CheckoutAttempt;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutAttemptRepository extends JpaRepository<CheckoutAttempt,UUID> {
    Optional<CheckoutAttempt> findByCustomerIdAndIdempotencyKey(String customerId,String idempotencyKey);
    List<CheckoutAttempt> findByStateAndCreatedAtBefore(CheckoutAttempt.State state,Instant deadline);
}
