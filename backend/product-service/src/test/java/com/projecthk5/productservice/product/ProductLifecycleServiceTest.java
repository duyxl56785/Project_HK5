package com.projecthk5.productservice.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.projecthk5.productservice.category.CategoryRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductLifecycleServiceTest {
    private final UUID storeId = UUID.randomUUID(), sellerId = UUID.randomUUID(), categoryId = UUID.randomUUID();
    private CategoryRepository categories;
    private FixturePolicy policy;
    private ProductLifecycleService service;

    @BeforeEach void setup() {
        categories = mock(CategoryRepository.class);
        when(categories.existsById(categoryId)).thenReturn(true);
        policy = new FixturePolicy();
        service = new ProductLifecycleService(policy, categories);
    }

    @Test void createEditAndModerateRetainsIdentitySkuAndExactPrice() {
        BigDecimal createPrice = new BigDecimal("12.00000"), editPrice = new BigDecimal("13.00000");
        ProductListing draft = service.create("owner", storeId, categoryId, "Sku-A", " Product ", createPrice, "VND");
        assertEquals(ProductStatus.DRAFT, draft.status());
        assertEquals("Product", draft.name());
        assertSame(createPrice, draft.unitPrice());
        ProductListing edited = service.edit("owner", draft, categoryId, "New", editPrice, "VND");
        ProductListing submitted = service.submit("owner", edited);
        ProductListing rejected = service.reject("admin", submitted, " Fix details ");
        ProductListing active = service.approve("admin", service.submit("owner", rejected));
        assertEquals(draft.id(), active.id());
        assertEquals("Sku-A", active.sku());
        assertEquals(new BigDecimal("13.00000"), active.unitPrice());
        assertSame(editPrice, active.unitPrice());
        assertEquals("VND", active.currency());
        assertEquals("Fix details", rejected.reviewReason());
        assertNull(active.reviewReason());
        assertEquals(ProductStatus.DRAFT, draft.status());
        assertEquals(ProductStatus.SUSPENDED, service.suspend("admin", active, "Policy").status());
        ProductListing inactive = service.deactivate("owner", active);
        assertEquals(ProductStatus.PENDING_REVIEW, service.submit("owner", inactive).status());
    }

    static Stream<Arguments> transitions() {
        return Arrays.stream(ProductStatus.values()).flatMap(state ->
                Stream.of("edit", "submit", "approve", "reject", "suspend", "deactivate")
                        .map(action -> Arguments.of(state, action)));
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void transitionMatrixRejectsEveryUnspecifiedTransition(ProductStatus state, String action) {
        ProductListing product = listing(state);
        boolean editable = state == ProductStatus.DRAFT || state == ProductStatus.REJECTED || state == ProductStatus.INACTIVE;
        boolean allowed = switch (action) {
            case "edit", "submit" -> editable;
            case "approve", "reject" -> state == ProductStatus.PENDING_REVIEW;
            case "suspend", "deactivate" -> state == ProductStatus.ACTIVE;
            default -> false;
        };
        if (allowed) {
            assertNotNull(operate(action, product));
        } else {
            assertEquals(ProductRuleException.Code.INVALID_STATE,
                    assertThrows(ProductRuleException.class, () -> operate(action, product)).getCode());
        }
        assertEquals(state, product.status());
    }

    private ProductListing operate(String action, ProductListing product) {
        return switch (action) {
            case "edit" -> service.edit("owner", product, categoryId, "Changed", BigDecimal.TEN, "VND");
            case "submit" -> service.submit("owner", product);
            case "approve" -> service.approve("admin", product);
            case "reject" -> service.reject("admin", product, "reason");
            case "suspend" -> service.suspend("admin", product, "reason");
            case "deactivate" -> service.deactivate("owner", product);
            default -> throw new IllegalArgumentException(action);
        };
    }

    @Test void foreignOwnerAndWrongRoleCannotManageOrModerate() {
        ProductListing product = listing(ProductStatus.PENDING_REVIEW);
        assertThrows(ProductRuleException.class,
                () -> service.create("other", storeId, categoryId, "SKU", "Name", BigDecimal.ONE, "VND"));
        assertThrows(ProductRuleException.class,
                () -> service.edit("other", listing(ProductStatus.DRAFT), categoryId, "Name", BigDecimal.ONE, "VND"));
        assertThrows(ProductRuleException.class, () -> service.submit("other", listing(ProductStatus.DRAFT)));
        assertThrows(ProductRuleException.class, () -> service.deactivate("other", listing(ProductStatus.ACTIVE)));
        assertThrows(ProductRuleException.class, () -> service.approve("owner", product));
        assertThrows(ProductRuleException.class, () -> service.reject("owner", product, "reason"));
        assertThrows(ProductRuleException.class, () -> service.suspend("owner", listing(ProductStatus.ACTIVE), "reason"));
        verify(categories, never()).existsById(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject", "suspend"})
    void adminCannotModerateOwnStoresProduct(String action) {
        policy.owner = "admin";
        ProductListing product = listing(action.equals("suspend") ? ProductStatus.ACTIVE : ProductStatus.PENDING_REVIEW);
        assertEquals(ProductRuleException.Code.ACCESS_DENIED,
                assertThrows(ProductRuleException.class, () -> operate(action, product)).getCode());
    }

    @Test void closedStoreOrSuspendedSellerCannotManageProduct() {
        policy.approved = false;
        assertThrows(ProductRuleException.class,
                () -> service.create("owner", storeId, categoryId, "SKU", "Name", BigDecimal.ONE, "VND"));
        policy.approved = true;
        policy.open = false;
        assertThrows(ProductRuleException.class, () -> service.submit("owner", listing(ProductStatus.DRAFT)));
    }

    @Test void identityMismatchOrMissingPolicyFactsFailClosed() {
        ProductAccessPolicy unavailable = mock(ProductAccessPolicy.class);
        ProductLifecycleService guarded = new ProductLifecycleService(unavailable, categories);
        assertEquals(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,
                assertThrows(ProductRuleException.class, () -> guarded.publicView(listing(ProductStatus.ACTIVE))).getCode());
        when(unavailable.requireSellerManagement("owner", storeId)).thenReturn(
                new ProductAccessPolicy.StoreAccess(UUID.randomUUID(), sellerId, "owner", true, true, false));
        assertEquals(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE, assertThrows(ProductRuleException.class,
                () -> guarded.create("owner", storeId, categoryId, "SKU", "Name", BigDecimal.ONE, "VND")).getCode());
        policy.owner = "Owner";
        assertThrows(ProductRuleException.class,
                () -> service.create("owner", storeId, categoryId, "SKU", "Name", BigDecimal.ONE, "VND"));
    }

    @Test void invalidActorIsRejectedBeforePolicyOrCategory() {
        ProductAccessPolicy guarded = mock(ProductAccessPolicy.class);
        ProductLifecycleService target = new ProductLifecycleService(guarded, categories);
        assertThrows(ProductRuleException.class,
                () -> target.create(null, storeId, categoryId, "SKU", "Name", BigDecimal.ONE, "VND"));
        verifyNoInteractions(guarded);
        verify(categories, never()).existsById(any());
    }

    @Test void missingCategoryCannotBeCreatedSubmittedApprovedOrPublic() {
        when(categories.existsById(categoryId)).thenReturn(false);
        assertEquals(ProductRuleException.Code.CATEGORY_NOT_FOUND, assertThrows(ProductRuleException.class,
                () -> service.create("owner", storeId, categoryId, "SKU", "Name", BigDecimal.ONE, "VND")).getCode());
        assertThrows(ProductRuleException.class, () -> service.submit("owner", listing(ProductStatus.DRAFT)));
        assertThrows(ProductRuleException.class, () -> service.approve("admin", listing(ProductStatus.PENDING_REVIEW)));
        assertTrue(service.publicView(listing(ProductStatus.ACTIVE)).isEmpty());
    }

    static Stream<Arguments> visibility() {
        return Arrays.stream(ProductStatus.values()).flatMap(status ->
                Stream.of(false, true).flatMap(approved -> Stream.of(false, true)
                        .map(open -> Arguments.of(status, approved, open))));
    }

    @ParameterizedTest
    @MethodSource("visibility")
    void onlyActiveProductFromApprovedSellerAndOpenStoreIsPublic(ProductStatus status, boolean approved, boolean open) {
        policy.approved = approved;
        policy.open = open;
        var view = service.publicView(listing(status));
        assertEquals(status == ProductStatus.ACTIVE && approved && open, view.isPresent());
        if (view.isPresent()) {
            assertEquals(sellerId, view.get().sellerId());
            assertEquals(storeId, view.get().storeId());
            assertEquals("Sku-A", view.get().sku());
            assertEquals(new BigDecimal("12.00000"), view.get().unitPrice());
            assertEquals("VND", view.get().currency());
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " SKU", "SKU ", "\t", "\u00a0SKU", "SKU\u00a0", "\u00a0",
            "\u2007SKU", "SKU\u2007", "\u202fSKU", "SKU\u202f", "\u2003SKU", "SKU\u2003"})
    void invalidSkuIsRejected(String sku) {
        assertThrows(ProductRuleException.class, () -> new ProductListing(UUID.randomUUID(), storeId, categoryId,
                sku, "Name", BigDecimal.ONE, "VND", ProductStatus.DRAFT, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sku-A", "sku-a", "SKU-A", "\u00e9-SKU", "e\u0301-SKU"})
    void validSkuRemainsExactAcrossCreateAndEdit(String sku) {
        ProductListing created = service.create("owner", storeId, categoryId, sku, "Name", BigDecimal.ONE, "VND");
        ProductListing edited = service.edit("owner", created, categoryId, "Edited", BigDecimal.TEN, "VND");
        assertEquals(sku, created.sku());
        assertEquals(sku, edited.sku());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"USD", "vnd", " VND", "VND "})
    void createAndEditRequireExplicitExactCurrency(String currency) {
        assertEquals(ProductRuleException.Code.INVALID_INPUT, assertThrows(ProductRuleException.class,
                () -> service.create("owner", storeId, categoryId, "SKU", "Name", BigDecimal.ONE, currency)).getCode());
        assertEquals(ProductRuleException.Code.INVALID_INPUT, assertThrows(ProductRuleException.class,
                () -> service.edit("owner", listing(ProductStatus.DRAFT), categoryId, "Name", BigDecimal.ONE, currency)).getCode());
    }

    @Test void invalidPriceAndMetadataAreRejectedWithoutRounding() {
        for (BigDecimal price : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-0.01")}) {
            assertThrows(ProductRuleException.class, () -> service.create("owner", storeId, categoryId, "SKU", "Name", price, "VND"));
        }
        assertThrows(ProductRuleException.class,
                () -> service.create("owner", storeId, categoryId, "s".repeat(129), "Name", BigDecimal.ONE, "VND"));
        assertThrows(ProductRuleException.class,
                () -> service.create("owner", storeId, categoryId, "SKU", "n".repeat(256), BigDecimal.ONE, "VND"));
        assertThrows(ProductRuleException.class,
                () -> service.reject("admin", listing(ProductStatus.PENDING_REVIEW), " "));
        assertThrows(ProductRuleException.class,
                () -> service.suspend("admin", listing(ProductStatus.ACTIVE), "r".repeat(501)));
        assertThrows(ProductRuleException.class,
                () -> service.create("owner", storeId, null, "SKU", "Name", BigDecimal.ONE, "VND"));
    }

    private ProductListing listing(ProductStatus status) {
        String reason = status == ProductStatus.REJECTED || status == ProductStatus.SUSPENDED ? "reason" : null;
        return new ProductListing(UUID.randomUUID(), storeId, categoryId, "Sku-A", "Name",
                new BigDecimal("12.00000"), "VND", status, reason);
    }

    private class FixturePolicy implements ProductAccessPolicy {
        String owner = "owner";
        boolean approved = true, open = true;

        @Override public StoreAccess requireSellerManagement(String actor, UUID id) {
            if (!"owner".equals(actor)) throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "Fixture role denied");
            return readStore(id);
        }
        @Override public StoreAccess requireModeration(String actor, UUID id) {
            if (!"admin".equals(actor)) throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED, "Fixture role denied");
            return readStore(id);
        }
        @Override public StoreAccess readStore(UUID id) {
            return new StoreAccess(storeId, sellerId, owner, approved, open, false);
        }
    }
}
