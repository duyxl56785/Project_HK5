package com.projecthk5.orderservice.service;

import com.projecthk5.orderservice.entity.CheckoutAttempt;
import com.projecthk5.orderservice.repository.CheckoutAttemptRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotencyCoordinatorTest {
    private final CheckoutAttemptRepository repo=mock(CheckoutAttemptRepository.class);
    private final IdempotencyCoordinator coordinator=new IdempotencyCoordinator(repo);

    @Test void sameKeyAndDifferentPayloadRejected() {
        CheckoutAttempt existing=new CheckoutAttempt("user1","same-key","hash-a");
        when(repo.findByCustomerIdAndIdempotencyKey("user1","same-key")).thenReturn(Optional.of(existing));
        ResponseStatusException error=assertThrows(ResponseStatusException.class,
            ()->coordinator.begin("user1","same-key","hash-b"));
        assertEquals(409,error.getStatusCode().value());
        verify(repo,never()).saveAndFlush(any());
    }
    @Test void repeatedSameKeyReturnsExistingNotNew() {
        CheckoutAttempt existing=new CheckoutAttempt("user1","same-key","hash-a");
        when(repo.findByCustomerIdAndIdempotencyKey("user1","same-key")).thenReturn(Optional.of(existing));
        var result=coordinator.begin("user1","same-key","hash-a");
        assertFalse(result.created());
        assertSame(existing,result.attempt());
        verify(repo,never()).saveAndFlush(any());
    }
}
