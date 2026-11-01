package com.projecthk5.orderservice.api;

import static org.junit.jupiter.api.Assertions.*;

import com.projecthk5.security.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class SellerOrderControllerTest {
    @Test void suspendedSellerCannotTakeOnNewOrders() {
        ApiException refused = assertThrows(ApiException.class, () -> SellerOrderController.requireAllowed(false, "accept"));
        assertEquals(HttpStatus.FORBIDDEN, refused.status());
    }

    @ParameterizedTest @ValueSource(strings = {"reject", "prepare", "ready", "dispatch", "deliver"})
    void suspendedSellerStillDeclinesAndFinishesOrders(String action) {
        assertDoesNotThrow(() -> SellerOrderController.requireAllowed(false, action));
    }

    @Test void approvedSellerAcceptsOrders() {
        assertDoesNotThrow(() -> SellerOrderController.requireAllowed(true, "accept"));
    }
}
