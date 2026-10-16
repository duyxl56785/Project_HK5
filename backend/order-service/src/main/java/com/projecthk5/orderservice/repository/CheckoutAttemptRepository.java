package com.projecthk5.orderservice.repository;

import com.projecthk5.orderservice.entity.CheckoutAttempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutAttemptRepository extends JpaRepository<CheckoutAttempt,UUID> {
    java.util.Optional<CheckoutAttempt> findByCustomerIdAndIdempotencyKey(String customerId,String idempotencyKey);
}
