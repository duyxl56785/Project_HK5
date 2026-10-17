package com.projecthk5.orderservice.repository;

import com.projecthk5.orderservice.entity.CheckoutAttempt;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckoutAttemptRepository extends JpaRepository<CheckoutAttempt,UUID> {
    Optional<CheckoutAttempt> findByCustomerIdAndIdempotencyKey(String customerId,String idempotencyKey);
    List<CheckoutAttempt> findByStateAndCreatedAtBefore(CheckoutAttempt.State state,Instant deadline);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CheckoutAttempt a set a.state = :nextState "
        + "where a.state = :processing and a.createdAt < :deadline")
    int flagStaleProcessing(@Param("processing") CheckoutAttempt.State processing,
        @Param("nextState") CheckoutAttempt.State nextState, @Param("deadline") Instant deadline);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CheckoutAttempt a set a.state = :nextState "
        + "where a.id = :id and a.state = :processing")
    int failIfProcessing(@Param("id") UUID id, @Param("processing") CheckoutAttempt.State processing,
        @Param("nextState") CheckoutAttempt.State nextState);
}
