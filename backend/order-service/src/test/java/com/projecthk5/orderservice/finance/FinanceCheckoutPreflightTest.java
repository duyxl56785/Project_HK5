package com.projecthk5.orderservice.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.api.CheckoutDtos.CheckoutRequest;
import com.projecthk5.orderservice.api.CheckoutDtos.ExpectedItem;
import com.projecthk5.orderservice.domain.MoneyRules;
import com.projecthk5.orderservice.domain.RequestFingerprint;
import com.projecthk5.orderservice.entity.CheckoutAttempt;
import com.projecthk5.orderservice.entity.Order;
import com.projecthk5.orderservice.ports.CartPort;
import com.projecthk5.orderservice.ports.CatalogPort;
import com.projecthk5.orderservice.ports.InventoryPort;
import com.projecthk5.orderservice.repository.OrderRepository;
import com.projecthk5.orderservice.service.CheckoutService;
import com.projecthk5.orderservice.service.IdempotencyCoordinator;
import com.projecthk5.orderservice.service.OrderWriteService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Current Checkout orchestration with mocked ports; no M01 signature, live identity or settlement. */
class FinanceCheckoutPreflightTest {
    private final CartPort cart = mock(CartPort.class);
    private final CatalogPort catalog = mock(CatalogPort.class);
    private final InventoryPort inventory = mock(InventoryPort.class);
    private final IdempotencyCoordinator keys = mock(IdempotencyCoordinator.class);
    private final OrderWriteService writer = mock(OrderWriteService.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final CheckoutRequest request = new CheckoutRequest(
        List.of(new ExpectedItem("p1", 1), new ExpectedItem("p2", 1)), null);
    private final String customer = "finance-customer", bearer = "Bearer fixture", key = "finance-preflight";

    private CheckoutAttempt fixture(boolean secondHouse, String secondSeller) {
        var attempt = new CheckoutAttempt(customer, key, RequestFingerprint.compute(request));
        when(keys.begin(eq(customer), eq(key), anyString()))
            .thenReturn(new IdempotencyCoordinator.BeginResult(attempt, true));
        when(cart.claim(bearer, key, null)).thenReturn(new CartPort.CartSnapshot(customer, 1, true,
            List.of(new CartPort.Item("p1", "store-a", "seller-a", 1, BigDecimal.TEN),
                new CartPort.Item("p2", "store-a", secondSeller, 1, BigDecimal.TEN))));
        when(catalog.verify("p1")).thenReturn(new CatalogPort.ProductSnapshot(
            "p1", "seller-a", "store-a", true, true, true, false, BigDecimal.TEN));
        when(catalog.verify("p2")).thenReturn(new CatalogPort.ProductSnapshot(
            "p2", secondSeller, "store-a", true, true, true, secondHouse, BigDecimal.TEN));
        return attempt;
    }

    private CheckoutService caller(MoneyRules money) {
        return new CheckoutService(cart, catalog, inventory, keys, writer, orders, new ObjectMapper(), money);
    }

    private void assertKnownPreflightFailure(CheckoutAttempt attempt) {
        verifyNoInteractions(inventory, writer, orders);
        verify(cart).release(bearer, key);
        verify(cart, never()).finalizeCart(anyString(), anyString());
        verify(keys).fail(attempt.getId(), false);
    }

    @Test void closedFinanceGateCannotReserveStockOrWriteOrder() {
        var attempt = fixture(false, "seller-a");
        var denied = new MoneyRules(false, "", "", "");
        var error = assertThrows(IllegalStateException.class,
            () -> caller(denied).checkout(customer, bearer, key, request));
        assertEquals("FINANCE_POLICY_NOT_APPROVED", error.getMessage());
        assertKnownPreflightFailure(attempt);
    }

    @Test void conflictingHouseFactsWithinOneStoreCannotSelectAnExemption() {
        var attempt = fixture(true, "seller-a");
        var error = assertThrows(IllegalStateException.class,
            () -> caller(new MoneyRules(true, "0.05", "0", "true"))
                .checkout(customer, bearer, key, request));
        assertEquals("Inconsistent store ownership", error.getMessage());
        assertKnownPreflightFailure(attempt);
    }

    @Test void matchingPerItemFactsWithTwoSellersCannotShareOneStoreOrder() {
        var attempt = fixture(false, "seller-b");
        var error = assertThrows(IllegalStateException.class,
            () -> caller(new MoneyRules(true, "0.05", "0", "true"))
                .checkout(customer, bearer, key, request));
        assertEquals("Inconsistent store ownership", error.getMessage());
        assertKnownPreflightFailure(attempt);
    }

    @Test void currentServiceRoundsOnceForTheStoreRatherThanEachLine() {
        var attempt = fixture(false, "seller-a");
        when(writer.savePlaced(any(Order.class), eq(attempt.getId()), anyString()))
            .thenAnswer(call -> call.getArgument(0));
        // Explicit synthetic policy, characterizing existing orchestration; not currency approval.
        var result = caller(new MoneyRules(true, "0.05", "0", "true"))
            .checkout(customer, bearer, key, request);
        var captured = ArgumentCaptor.forClass(Order.class);
        verify(writer).savePlaced(captured.capture(), eq(attempt.getId()), anyString());
        var children = captured.getValue().getSellerOrders();
        assertEquals(1, children.size());
        var child = children.get(0);
        assertEquals(2, child.getItems().size());
        assertEquals(new BigDecimal("20"), child.getGross());
        assertEquals(BigDecimal.ONE, child.getPlatformFee());
        assertEquals(new BigDecimal("19"), child.getGross().subtract(child.getPlatformFee()));
        assertEquals(new BigDecimal("20"), result.total());
        verify(inventory).reserve(eq(attempt.getId()), argThat(lines -> lines.size() == 2));
        verify(cart).finalizeCart(bearer, key);
        verify(cart, never()).release(anyString(), anyString());
        verify(keys, never()).fail(any(), anyBoolean());
        // Derived net is arithmetic; no immutable net column, payment or settlement is created here.
    }
}
