package com.projecthk5.productservice.product.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projecthk5.productservice.product.ProductApplicationService;
import com.projecthk5.productservice.product.ProductListing;
import com.projecthk5.productservice.product.ProductRuleException;
import com.projecthk5.productservice.product.ProductStatus;
import com.projecthk5.productservice.product.persistence.ProductPersistenceAdapter.StoredProduct;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductManagementHandlerTest {
    private final ProductApplicationService application = mock(ProductApplicationService.class);
    private final ProductManagementJson json = new ProductManagementJson();
    private final UUID id = UUID.randomUUID(), store = UUID.randomUUID(), category = UUID.randomUUID();
    private final StoredProduct stored = new StoredProduct(new ProductListing(id, store, category,
            "sku", "Name", BigDecimal.ONE, "VND", ProductStatus.DRAFT, null), 7);

    @ParameterizedTest @NullSource @ValueSource(strings = {"", " ", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void invalidAuthorityOutputFailsBeforeParsingOrDatabaseAccess(String actor) {
        var handler = new ProductManagementHandler(() -> actor, application, json);
        for (Runnable action : new Runnable[] {
                () -> handler.create("bad json"), () -> handler.edit(null, "bad json"),
                () -> handler.submit(null, "bad json"), () -> handler.approve(null, "bad json"),
                () -> handler.reject(null, "bad json"), () -> handler.suspend(null, "bad json"),
                () -> handler.deactivate(null, "bad json"), () -> handler.findById(null)}) {
            var failure = assertThrows(ProductRuleException.class, action::run);
            assertEquals(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, failure.getCode());
        }
        verifyNoInteractions(application);
    }

    @Test void authorityDenialIsNeverConvertedToSuccessOrCallerSuppliedIdentity() {
        ProductManagementAuthority authority = () -> { throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "denied"); };
        var handler = new ProductManagementHandler(authority, application, json);
        assertThrows(ProductRuleException.class, () -> handler.create("{\"role\":\"ADMIN\"}"));
        verifyNoInteractions(application);
    }

    @Test void exactVerifiedActorAndEveryCommandAreDelegatedToExistingPolicyGuardedApplication() {
        String actor = "Account-Exact";
        var handler = new ProductManagementHandler(() -> actor, application, json);
        String version = "{\"expectedVersion\":\"7\"}";
        String moderation = "{\"expectedVersion\":\"7\",\"reason\":\" Reason \"}";
        when(application.create(actor, store, category, "sku", "Name", BigDecimal.ONE, "VND")).thenReturn(stored);
        when(application.edit(actor, id, 7, category, "Name", BigDecimal.ONE, "VND")).thenReturn(stored);
        when(application.submit(actor, id, 7)).thenReturn(stored);
        when(application.approve(actor, id, 7)).thenReturn(stored);
        when(application.reject(actor, id, 7, " Reason ")).thenReturn(stored);
        when(application.suspend(actor, id, 7, " Reason ")).thenReturn(stored);
        when(application.deactivate(actor, id, 7)).thenReturn(stored);
        when(application.findById(actor, id)).thenReturn(stored);
        String create = "{\"storeId\":\"%s\",\"categoryId\":\"%s\",\"sku\":\"sku\",\"name\":\"Name\",\"unitPrice\":\"1\",\"currency\":\"VND\"}".formatted(store, category);
        String edit = "{\"categoryId\":\"%s\",\"name\":\"Name\",\"unitPrice\":\"1\",\"currency\":\"VND\",\"expectedVersion\":\"7\"}".formatted(category);
        var expected = ProductManagementResponse.from(stored);
        assertEquals(expected, handler.create(create));
        assertEquals(expected, handler.edit(id, edit));
        assertEquals(expected, handler.submit(id, version));
        assertEquals(expected, handler.approve(id, version));
        assertEquals(expected, handler.reject(id, moderation));
        assertEquals(expected, handler.suspend(id, moderation));
        assertEquals(expected, handler.deactivate(id, version));
        assertEquals(expected, handler.findById(id));
        verify(application).create(actor, store, category, "sku", "Name", BigDecimal.ONE, "VND");
        verify(application).edit(actor, id, 7, category, "Name", BigDecimal.ONE, "VND");
        verify(application).submit(actor, id, 7);
        verify(application).approve(actor, id, 7);
        verify(application).reject(actor, id, 7, " Reason ");
        verify(application).suspend(actor, id, 7, " Reason ");
        verify(application).deactivate(actor, id, 7);
        verify(application).findById(actor, id);
        verifyNoMoreInteractions(application);
    }
}
