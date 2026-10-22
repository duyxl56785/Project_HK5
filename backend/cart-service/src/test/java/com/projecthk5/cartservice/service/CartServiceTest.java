package com.projecthk5.cartservice.service;

import com.projecthk5.cartservice.api.CartDtos.*;
import com.projecthk5.cartservice.catalog.CartCatalogPort;
import com.projecthk5.cartservice.entity.Cart;
import com.projecthk5.cartservice.repository.CartRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartServiceTest {
    private final CartRepository repo=mock(CartRepository.class);
    private final CartCatalogPort catalog=mock(CartCatalogPort.class);
    private final CartService service=new CartService(repo,catalog);

    @Test void addUpdateRemoveAndTrustedStore() {
        Cart cart=new Cart("customer-a");
        when(repo.findByCustomerId("customer-a")).thenReturn(Optional.of(cart));
        when(repo.findForUpdate("customer-a")).thenReturn(Optional.of(cart));
        when(repo.saveAndFlush(cart)).thenReturn(cart);
        when(catalog.resolve("product-1")).thenReturn(new CartCatalogPort.ProductSnapshot("product-1","store-1","seller-1",true,new java.math.BigDecimal("10000")));
        CartView afterAdd=service.add("customer-a",new AddItem("product-1",2));
        assertEquals("store-1",afterAdd.items().get(0).storeId());
        assertEquals("seller-1",afterAdd.items().get(0).sellerId());
        assertEquals(2,afterAdd.items().get(0).quantity());
        assertEquals(5,service.update("customer-a","product-1",5).items().get(0).quantity());
        assertTrue(service.remove("customer-a","product-1").items().isEmpty());
    }
    @Test void claimedCartRejectsWriteAndReleaseWorks() {
        Cart cart=new Cart("customer-a");
        when(repo.findByCustomerId("customer-a")).thenReturn(Optional.of(cart));
        when(repo.findForUpdate("customer-a")).thenReturn(Optional.of(cart));
        when(repo.saveAndFlush(cart)).thenReturn(cart);
        when(catalog.resolve("p")).thenReturn(new CartCatalogPort.ProductSnapshot("p","s","seller",true,new java.math.BigDecimal("10000")));
        service.add("customer-a",new AddItem("p",1));
        service.claim("customer-a","request-1",null);
        assertThrows(IllegalStateException.class, () -> service.update("customer-a","p",2));
        assertThrows(IllegalStateException.class, () -> service.release("customer-a","different-request"));
        service.release("customer-a","request-1");
        assertEquals(2,service.update("customer-a","p",2).items().get(0).quantity());
    }
    @Test void invalidProductIdsFromDirectCallDoNotInvokeCatalogOrSave() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service.add("customer-a", new AddItem(" ", 1)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service.add("customer-a", new AddItem(null, 1)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service.add("customer-a", null));
        verifyNoInteractions(catalog, repo);
    }

    @Test void untrustedBlankStoreOrSellerNeverPersistsCart() {
        when(catalog.resolve("p")).thenReturn(
            new CartCatalogPort.ProductSnapshot("p", "  ", "seller", true, java.math.BigDecimal.ONE));
        assertThrows(IllegalStateException.class,
            () -> service.add("customer-a", new AddItem("p", 1)));
        when(catalog.resolve("p")).thenReturn(
            new CartCatalogPort.ProductSnapshot("p", "store", "", true, java.math.BigDecimal.ONE));
        assertThrows(IllegalStateException.class,
            () -> service.add("customer-a", new AddItem("p", 1)));
        verifyNoInteractions(repo);
    }

    @Test void priceThatPostgresWouldRoundOrOverflowNeverTouchesCart() {
        for (String price : new String[]{"1.001", "1.005", "999999999999999999.99"}) {
            when(catalog.resolve("p")).thenReturn(new CartCatalogPort.ProductSnapshot(
                    "p", "store", "seller", true, new java.math.BigDecimal(price)));
            assertThrows(IllegalArgumentException.class,
                    () -> service.add("customer-a", new AddItem("p", 1)), price);
        }
        verifyNoInteractions(repo);
    }

    @Test void representablePriceCanRetainExactValueWithoutChoosingMoneyPolicy() {
        Cart cart = new Cart("customer-a");
        when(repo.findForUpdate("customer-a")).thenReturn(Optional.of(cart));
        when(repo.saveAndFlush(cart)).thenReturn(cart);
        when(catalog.resolve("p")).thenReturn(new CartCatalogPort.ProductSnapshot(
                "p", "store", "seller", true, new java.math.BigDecimal("1.2300")));
        CartView result = service.add("customer-a", new AddItem("p", 1));
        assertEquals(0, result.items().get(0).unitPriceSnapshot().compareTo(new java.math.BigDecimal("1.23")));
        assertEquals(new java.math.BigDecimal("1.23"),
                CartAmountStorageBounds.requireExactNonnegative(new java.math.BigDecimal("1.2300")));
        // Zero is allowed by the CURRENT database bound; business min is still M01 PENDING.
        assertEquals(new java.math.BigDecimal("0.00"),
                CartAmountStorageBounds.requireExactNonnegative(java.math.BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> CartAmountStorageBounds.requireExactNonnegative(new java.math.BigDecimal("-0.01")));
    }

    @Test void nullOrMalformedClaimKeyCannotSilentlySucceedOnUnclaimedCart() {
        Cart cart = new Cart("customer-a");
        assertThrows(IllegalArgumentException.class, () -> cart.claim(null, null));
        assertThrows(IllegalArgumentException.class, () -> cart.claim("", null));
        assertThrows(IllegalArgumentException.class, () -> cart.claim("  ", null));
        assertThrows(IllegalArgumentException.class, () -> cart.claim(" key", null));
        assertThrows(IllegalArgumentException.class, () -> cart.claim("key ", null));
        assertThrows(IllegalArgumentException.class, () -> cart.claim("k".repeat(129), null));
        assertNull(cart.getCheckoutClaim());
        // After validating a key, the existing nonempty-Cart rule must still apply.
        assertThrows(IllegalStateException.class, () -> cart.claim("valid-key", null));
        assertNull(cart.getCheckoutClaim());
    }

    @Test void finalizationClearsOnlyMatchingClaim() {
        Cart cart=new Cart("customer-a");
        when(repo.findByCustomerId("customer-a")).thenReturn(Optional.of(cart));
        when(repo.findForUpdate("customer-a")).thenReturn(Optional.of(cart));
        when(repo.saveAndFlush(cart)).thenReturn(cart);
        when(catalog.resolve("p")).thenReturn(new CartCatalogPort.ProductSnapshot("p","s","seller",true,new java.math.BigDecimal("10000")));
        service.add("customer-a",new AddItem("p",1));
        service.claim("customer-a","request-1",null);
        assertThrows(IllegalStateException.class, () -> service.finalizeCheckout("customer-a","another"));
        service.finalizeCheckout("customer-a","request-1");
        assertTrue(service.view("customer-a").items().isEmpty());
    }
}
