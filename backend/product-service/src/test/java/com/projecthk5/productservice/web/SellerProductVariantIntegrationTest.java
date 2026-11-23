package com.projecthk5.productservice.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.productservice.catalog.CurrentStoreFactsSource.StoreFacts;
import com.projecthk5.productservice.product.ProductAccessPolicy.StoreAccess;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Size/color variants through the real Seller, Admin, Catalog and internal Cart/Order endpoints. */
@Testcontainers
@Tag("postgresql")
@SpringBootTest(properties = {"CART_PRODUCT_TOKEN=variant-cart-token", "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1"})
@AutoConfigureMockMvc
class SellerProductVariantIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final String OWNER = "variant-owner";

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @MockBean SellerStoreClient stores;
    @MockBean DependencyClients inventory;

    private final Map<String, Long> stock = new ConcurrentHashMap<>();
    private UUID store, seller, category;

    @BeforeEach void storeAndInventory() {
        store = UUID.randomUUID();
        seller = UUID.randomUUID();
        category = UUID.randomUUID();
        db.update("INSERT INTO categories(id,name) VALUES (?,?)", category, "Quần áo " + category);
        StoreAccess access = new StoreAccess(store, seller, OWNER, true, true, false);
        SellerStoreClient.Facts facts = new SellerStoreClient.Facts(store, seller, OWNER, "Shop", "shop-" + store, true, true, false);
        when(stores.currentManagementStore(OWNER)).thenReturn(store);
        when(stores.requireSellerManagement(OWNER, store)).thenReturn(access);
        when(stores.requireModeration(anyString(), eq(store))).thenReturn(access);
        when(stores.readStore(store)).thenReturn(access);
        when(stores.publicStores()).thenReturn(List.of(facts));
        when(stores.facts(any())).thenReturn(List.of(facts));
        when(stores.loadCurrent(any())).thenReturn(List.of(new StoreFacts(store, seller, OWNER, true, true, false, true)));
        when(inventory.balance(anyString())).thenAnswer(call -> {
            Long available = stock.get(call.<String>getArgument(0));
            return available == null ? null : new DependencyClients.Balance(call.getArgument(0), available, 0);
        });
        when(inventory.setAvailable(anyString(), any(Long.class), anyString())).thenAnswer(call -> {
            stock.put(call.getArgument(0), call.getArgument(1));
            return new DependencyClients.Balance(call.getArgument(0), call.getArgument(1), 0);
        });
    }

    @Test void createWithVariantsMakesOneProductPerOptionWithItsOwnSkuPriceAndStock() throws Exception {
        String sku = sku();
        JsonNode created = created(sku);

        assertEquals("Áo thun (M / Đỏ)", created.at("/product/name").asText());
        assertEquals(sku + "-M-DO", created.at("/product/sku").asText());
        JsonNode variants = created.get("variants");
        assertEquals(3, variants.size());
        assertEquals(List.of("M", "L", "M"), List.of(variants.get(0).get("size").asText(),
                variants.get(1).get("size").asText(), variants.get(2).get("size").asText()));
        assertEquals("Xanh", variants.get(2).get("color").asText());
        assertEquals("160000", variants.get(1).get("unitPrice").asText());
        assertEquals("Áo thun (L / Đỏ)", variants.get(1).get("name").asText());
        assertEquals(5, variants.get(0).get("available").asLong());
        assertEquals(1, created.at("/variant/position").asInt());

        UUID lead = id(created);
        assertEquals(3, db.queryForObject("SELECT count(*) FROM product_variants WHERE group_id=?", Integer.class, lead));
        assertEquals("DRAFT", db.queryForObject("SELECT status FROM products WHERE inventory_sku=?", String.class, sku + "-L-DO"));
        assertEquals("Áo dày", db.queryForObject("SELECT description FROM product_details WHERE product_id=?", String.class, lead));
        verify(inventory).setAvailable(eq(sku + "-M-DO"), eq(5L), anyString());
        verify(inventory, never()).setAvailable(eq(sku + "-L-DO"), any(Long.class), anyString());
        verify(inventory).setAvailable(eq(sku + "-M-XA"), eq(2L), anyString());
    }

    @Test void refusedGroupPersistsNothing() throws Exception {
        String sku = sku();
        MockHttpServletResponse duplicate = send(post("/api/seller/products"), seller(), Map.of(
                "categoryId", category, "name", "Áo", "variants", List.of(
                        variant("M", "Đỏ", sku + "-A", 100000, 1), variant("m", "đỏ", sku + "-B", 100000, 1))));
        assertEquals(400, duplicate.getStatus(), duplicate.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(duplicate.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("VARIANT_INVALID"));

        db.update("INSERT INTO products(id,store_id,category_id,inventory_sku,name,unit_price,currency,status) "
                + "VALUES (?,?,?,?,'Taken',1000,'VND','DRAFT')", UUID.randomUUID(), store, category, sku + "-TAKEN");
        MockHttpServletResponse taken = send(post("/api/seller/products"), seller(), Map.of(
                "categoryId", category, "name", "Áo", "variants", List.of(
                        variant("M", null, sku + "-FREE", 100000, 0), variant("L", null, sku + "-TAKEN", 100000, 0))));
        assertEquals(409, taken.getStatus(), taken.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM products WHERE inventory_sku=?", Integer.class, sku + "-FREE"));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM product_variants v JOIN products p ON p.id=v.product_id "
                + "WHERE p.store_id=?", Integer.class, store));
    }

    @Test void plainProductStillNeedsItsSkuAndPrice() throws Exception {
        MockHttpServletResponse missing = send(post("/api/seller/products"), seller(), Map.of(
                "categoryId", category, "name", "Mũ", "unitPrice", 50000));
        assertEquals(400, missing.getStatus(), missing.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        MockHttpServletResponse plain = send(post("/api/seller/products"), seller(), Map.of(
                "categoryId", category, "name", "Mũ", "sku", sku(), "unitPrice", 50000));
        assertEquals(201, plain.getStatus(), plain.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode body = json.readTree(plain.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(body.get("variant").isNull());
        assertEquals(0, body.get("variants").size());
    }

    @Test void leadLifecycleCarriesTheVariantsThatFollowIt() throws Exception {
        String sku = sku();
        JsonNode created = created(sku);
        UUID lead = id(created), large = variantId(created, 1), blue = variantId(created, 2);

        assertEquals(200, send(post("/api/seller/products/" + lead + "/submit"), seller(), version(lead)).getStatus());
        assertEquals(List.of("PENDING_REVIEW", "PENDING_REVIEW", "PENDING_REVIEW"), statuses(lead, large, blue));

        assertEquals(200, send(post("/api/admin/products/" + lead + "/approve"), admin(), version(lead)).getStatus());
        assertEquals(List.of("ACTIVE", "ACTIVE", "ACTIVE"), statuses(lead, large, blue));

        // A variant stopped on its own moves alone and is skipped by the next group decision.
        assertEquals(200, send(post("/api/seller/products/" + blue + "/deactivate"), seller(), version(blue)).getStatus());
        assertEquals(List.of("ACTIVE", "ACTIVE", "INACTIVE"), statuses(lead, large, blue));
        assertEquals(200, send(post("/api/admin/products/" + lead + "/suspend"), admin(),
                Map.of("expectedVersion", Long.toString(versionOf(lead)), "reason", "Kiểm tra")).getStatus());
        assertEquals(List.of("SUSPENDED", "SUSPENDED", "INACTIVE"), statuses(lead, large, blue));
        assertEquals(200, send(post("/api/admin/products/" + lead + "/unsuspend"), admin(), version(lead)).getStatus());
        assertEquals(List.of("ACTIVE", "ACTIVE", "INACTIVE"), statuses(lead, large, blue));

        // Pausing and resuming the group carries every stopped variant back with it.
        assertEquals(200, send(post("/api/seller/products/" + lead + "/deactivate"), seller(), version(lead)).getStatus());
        assertEquals(List.of("INACTIVE", "INACTIVE", "INACTIVE"), statuses(lead, large, blue));
        assertEquals(200, send(post("/api/seller/products/" + lead + "/activate"), seller(), version(lead)).getStatus());
        assertEquals(List.of("ACTIVE", "ACTIVE", "ACTIVE"), statuses(lead, large, blue));

        // A stale lead version refuses the whole step, variants included.
        assertEquals(409, send(post("/api/seller/products/" + lead + "/deactivate"), seller(),
                Map.of("expectedVersion", Long.toString(versionOf(lead) - 1))).getStatus());
        assertEquals(List.of("ACTIVE", "ACTIVE", "ACTIVE"), statuses(lead, large, blue));
    }

    private List<String> statuses(UUID... products) {
        return java.util.Arrays.stream(products).map(this::status).toList();
    }

    @Test void catalogListsTheGroupOnceAndTheDetailOffersEachActiveVariant() throws Exception {
        String sku = sku();
        JsonNode created = created(sku);
        UUID lead = id(created), large = variantId(created, 1), blue = variantId(created, 2);
        db.update("UPDATE products SET status='ACTIVE' WHERE id IN (?,?)", lead, large);

        JsonNode page = json.readTree(send(get("/api/catalog/products?storeId=" + store), null, null).getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1, page.get("total").asLong());
        assertEquals(lead.toString(), page.at("/items/0/id").asText());
        assertEquals("Áo thun", page.at("/items/0/name").asText());

        MockHttpServletResponse viaVariant = send(get("/api/catalog/products/" + large), null, null);
        assertEquals(200, viaVariant.getStatus(), viaVariant.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode detail = json.readTree(viaVariant.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(lead.toString(), detail.get("id").asText());
        assertEquals("Áo thun", detail.get("name").asText());
        assertEquals("Áo dày", detail.get("description").asText());
        JsonNode offers = detail.get("variants");
        assertEquals(2, offers.size(), "the inactive blue variant is not offered");
        assertEquals(large.toString(), offers.get(1).get("id").asText());
        assertEquals("L", offers.get(1).get("size").asText());
        assertEquals("Đỏ", offers.get(1).get("color").asText());
        assertEquals("160000", offers.get(1).get("unitPrice").asText());
        assertEquals(0, offers.get(1).get("available").asLong());
        assertEquals(5, offers.get(0).get("available").asLong());
        assertEquals(5, detail.get("available").asLong());
        assertFalse(offers.toString().contains(blue.toString()));

        JsonNode plainPage = json.readTree(send(get("/api/catalog/products/" + lead), null, null).getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(2, plainPage.get("variants").size());
    }

    @Test void cartAndOrderStillResolveEachVariantAsItsOwnProduct() throws Exception {
        String sku = sku();
        JsonNode created = created(sku);
        UUID large = variantId(created, 1);
        db.update("UPDATE products SET status='ACTIVE' WHERE id=?", large);

        MockHttpServletResponse snapshot = http.perform(get("/internal/catalog/products/" + large)
                .header("X-Internal-Token", "variant-cart-token")).andReturn().getResponse();
        assertEquals(200, snapshot.getStatus(), snapshot.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode body = json.readTree(snapshot.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(sku + "-L-DO", body.get("sku").asText());
        assertEquals("Áo thun (L / Đỏ)", body.get("name").asText());
        assertEquals(160000, body.get("unitPrice").asInt());
    }

    @Test void variantNamesShowTheGroupImage() throws Exception {
        JsonNode created = created(sku());
        UUID lead = id(created), large = variantId(created, 1), image = UUID.randomUUID();
        db.update("INSERT INTO product_images(id,product_id,storage_key,media_type,width,height,size_bytes,display_order,created_at) "
                + "VALUES (?,?,?,'image/png',4,4,10,1,now())", image, lead, UUID.randomUUID().toString());

        JsonNode names = json.readTree(send(get("/api/catalog/products/names?ids=" + large), null, null).getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("Áo thun (L / Đỏ)", names.at("/0/name").asText());
        assertEquals("/api/catalog/images/" + image, names.at("/0/imageUrl").asText());
    }

    @Test void editingTheLeadRenamesTheGroupAndKeepsEachPrice() throws Exception {
        JsonNode created = created(sku());
        UUID lead = id(created), large = variantId(created, 1);

        MockHttpServletResponse edited = send(put("/api/seller/products/" + lead), seller(), Map.of(
                "categoryId", category, "name", "Áo polo", "unitPrice", 155000, "description", "Mới",
                "expectedVersion", Long.toString(versionOf(lead))));
        assertEquals(200, edited.getStatus(), edited.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("Áo polo (M / Đỏ)", name(lead));
        assertEquals("Áo polo (L / Đỏ)", name(large));
        assertEquals(155000, db.queryForObject("SELECT unit_price FROM products WHERE id=?", Integer.class, lead));
        assertEquals(160000, db.queryForObject("SELECT unit_price FROM products WHERE id=?", Integer.class, large));
    }

    @Test void aVariantChangesOnlyItsPriceAndStock() throws Exception {
        JsonNode created = created(sku());
        UUID lead = id(created), large = variantId(created, 1);

        MockHttpServletResponse renamed = send(put("/api/seller/products/" + large), seller(), Map.of(
                "categoryId", category, "name", "Tên khác", "unitPrice", 170000,
                "expectedVersion", Long.toString(versionOf(large))));
        assertEquals(409, renamed.getStatus(), renamed.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(renamed.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("VARIANT_GROUP_FIELD"));

        MockHttpServletResponse priced = send(put("/api/seller/products/" + large), seller(), Map.of(
                "categoryId", category, "name", "Áo thun", "unitPrice", 170000,
                "expectedVersion", Long.toString(versionOf(large))));
        assertEquals(200, priced.getStatus(), priced.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("Áo thun (L / Đỏ)", name(large));
        assertEquals(170000, db.queryForObject("SELECT unit_price FROM products WHERE id=?", Integer.class, large));
        assertEquals(150000, db.queryForObject("SELECT unit_price FROM products WHERE id=?", Integer.class, lead));

        assertEquals(200, send(put("/api/seller/products/" + large + "/stock"), seller(), Map.of("available", 9)).getStatus());
        assertEquals(9L, stock.get(db.queryForObject("SELECT inventory_sku FROM products WHERE id=?", String.class, large)));
    }

    private JsonNode created(String sku) throws Exception {
        MockHttpServletResponse response = send(post("/api/seller/products"), seller(), Map.of(
                "categoryId", category, "name", "Áo thun", "description", "Áo dày", "variants", List.of(
                        variant("M", "Đỏ", sku + "-M-DO", 150000, 5),
                        variant("L", "Đỏ", sku + "-L-DO", 160000, 0),
                        variant("M", "Xanh", sku + "-M-XA", 150000, 2))));
        assertEquals(201, response.getStatus(), response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        return json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static Map<String, Object> variant(String size, String color, String sku, long price, long initialStock) {
        Map<String, Object> variant = new java.util.HashMap<>(Map.of("sku", sku, "unitPrice", price, "initialStock", initialStock));
        variant.put("size", size);
        variant.put("color", color);
        return variant;
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, JwtRequestPostProcessor token, Object body)
            throws Exception {
        if (token != null) request.with(token);
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body));
        return http.perform(request).andReturn().getResponse();
    }

    private static JwtRequestPostProcessor seller() { return token(OWNER, "SELLER"); }
    private static JwtRequestPostProcessor admin() { return token("variant-admin", "ADMIN"); }

    private static JwtRequestPostProcessor token(String actor, String role) {
        return jwt().jwt(j -> j.subject(actor).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private Map<String, String> version(UUID product) { return Map.of("expectedVersion", Long.toString(versionOf(product))); }
    private long versionOf(UUID product) { return db.queryForObject("SELECT version FROM products WHERE id=?", Long.class, product); }
    private String status(UUID product) { return db.queryForObject("SELECT status FROM products WHERE id=?", String.class, product); }
    private String name(UUID product) { return db.queryForObject("SELECT name FROM products WHERE id=?", String.class, product); }
    private static UUID id(JsonNode view) { return UUID.fromString(view.at("/product/id").asText()); }
    private static UUID variantId(JsonNode view, int index) { return UUID.fromString(view.get("variants").get(index).get("id").asText()); }
    private static String sku() { return "VAR-" + UUID.randomUUID().toString().substring(0, 8); }
}
