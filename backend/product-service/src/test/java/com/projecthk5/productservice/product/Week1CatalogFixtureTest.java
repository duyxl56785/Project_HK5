package com.projecthk5.productservice.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.productservice.category.CategoryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Fixture/domain verification, deliberately not a database seed or a Checkout E2E test. */
class Week1CatalogFixtureTest {
    @Test void twoStoreFixtureHasStableDistinctIdentitiesAndUsesActualDomainTransitions() throws Exception {
        JsonNode fixture;
        try (var input = getClass().getResourceAsStream("/fixtures/week1-catalog.json")) {
            assertNotNull(input);
            fixture = new ObjectMapper().readTree(input);
        }
        assertEquals("TEST_ONLY_NOT_PERSISTED", fixture.path("readiness").asText());
        Set<UUID> categoryIds = new HashSet<>();
        fixture.path("categories").forEach(c -> assertTrue(categoryIds.add(UUID.fromString(c.path("id").asText()))));
        Map<UUID, ProductAccessPolicy.StoreAccess> stores = new HashMap<>();
        Set<String> owners = new HashSet<>(), slugs = new HashSet<>();
        Set<UUID> sellers = new HashSet<>();
        for (JsonNode row : fixture.path("stores")) {
            UUID id = UUID.fromString(row.path("storeId").asText());
            UUID seller = UUID.fromString(row.path("sellerId").asText());
            String owner = row.path("ownerReference").asText();
            assertNotEquals(seller, UUID.fromString(owner));
            assertNotEquals(id, seller);
            assertTrue(owners.add(owner));
            assertTrue(sellers.add(seller));
            assertTrue(slugs.add(row.path("slug").asText()));
            assertNull(stores.put(id, new ProductAccessPolicy.StoreAccess(id, seller, owner,
                    row.path("sellerApproved").asBoolean(), row.path("storeOpen").asBoolean(),
                    row.path("houseStore").asBoolean())));
        }
        assertEquals(2, stores.size());
        String admin = fixture.path("adminAccountId").asText();
        assertFalse(owners.contains(admin));
        ProductAccessPolicy policy = new ProductAccessPolicy() {
            @Override public StoreAccess requireSellerManagement(String actor, UUID id) {
                StoreAccess store = readStore(id);
                if (!store.ownerReference().equals(actor)) throw new ProductRuleException(
                        ProductRuleException.Code.ACCESS_DENIED, "Fixture owner denied");
                return store;
            }
            @Override public StoreAccess requireModeration(String actor, UUID id) {
                if (!admin.equals(actor)) throw new ProductRuleException(
                        ProductRuleException.Code.ACCESS_DENIED, "Fixture moderator denied");
                return readStore(id);
            }
            @Override public StoreAccess readStore(UUID id) { return stores.get(id); }
        };
        CategoryRepository categories = mock(CategoryRepository.class);
        when(categories.existsById(any())).thenAnswer(call -> categoryIds.contains(call.getArgument(0)));
        ProductLifecycleService service = new ProductLifecycleService(policy, categories);
        Set<UUID> productIds = new HashSet<>();
        Set<String> skus = new HashSet<>();
        List<ProductLifecycleService.CatalogProduct> publicProducts = new ArrayList<>();
        for (JsonNode row : fixture.path("products")) {
            UUID id = UUID.fromString(row.path("productId").asText());
            UUID storeId = UUID.fromString(row.path("storeId").asText());
            assertTrue(productIds.add(id));
            assertTrue(skus.add(row.path("sku").asText()));
            assertNotEquals(id.toString(), row.path("sku").asText());
            ProductListing product = new ProductListing(id, storeId, UUID.fromString(row.path("categoryId").asText()),
                    row.path("sku").asText(), row.path("name").asText(), new BigDecimal(row.path("unitPrice").asText()),
                    ProductStatus.DRAFT, null);
            String owner = stores.get(storeId).ownerReference();
            if ("ACTIVE".equals(row.path("status").asText())) {
                product = service.approve(admin, service.submit(owner, product));
                assertTrue(row.path("suggestedAvailableQuantity").asLong() > 0);
            }
            service.publicView(product).ifPresent(publicProducts::add);
        }
        assertEquals(4, productIds.size());
        assertEquals(3, publicProducts.size());
        assertEquals(2, publicProducts.stream().map(ProductLifecycleService.CatalogProduct::storeId).distinct().count());
    }
}
