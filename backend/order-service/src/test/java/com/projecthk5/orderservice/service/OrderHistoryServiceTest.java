package com.projecthk5.orderservice.service;

import com.projecthk5.orderservice.api.OrderHistoryController;
import com.projecthk5.orderservice.repository.OrderHistoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderHistoryServiceTest {
    private final OrderHistoryRepository repo = mock(OrderHistoryRepository.class);
    private final OrderHistoryService service = new OrderHistoryService(repo);

    @ParameterizedTest @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void invalidPaginationDoesNotQueryStorage(int page, int size) {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.list("customer", page, size)).getStatusCode().value());
        verifyNoInteractions(repo);
    }
    @Test void missingAndForeignOrderHaveTheSameNotFoundResponse() {
        UUID id = UUID.randomUUID();
        when(repo.detail("customer-B", id)).thenReturn(Optional.empty());
        var e = assertThrows(ResponseStatusException.class, () -> service.detail("customer-B", id));
        assertEquals(404, e.getStatusCode().value());
        assertEquals("Order not found", e.getReason());
        verify(repo).detail("customer-B", id);
    }
    @Test void missingIdentityIsRejectedBeforeStorage() {
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.list(null, 0, 20)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.detail(" ", UUID.randomUUID())).getStatusCode().value());
        verifyNoInteractions(repo);
    }
    @Test void controllerUsesVerifiedSubjectExactly() {
        var jwt = Jwt.withTokenValue("unit-only").header("alg", "HS256").subject("exact-Customer-ID")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claim("roles", List.of("CUSTOMER")).build();
        new OrderHistoryController(service).list(new JwtAuthenticationToken(jwt), 0, 20);
        verify(repo).list("exact-Customer-ID", 0, 20);
    }
    @Test void nonCustomerCannotReachStorage() {
        var jwt = Jwt.withTokenValue("unit-only").header("alg", "HS256").subject("seller")
                .claim("roles", List.of("SELLER")).build();
        var controller = new OrderHistoryController(service);
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.list(new JwtAuthenticationToken(jwt), 0, 20)).getStatusCode().value());
        verifyNoInteractions(repo);
    }
}
