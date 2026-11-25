package com.projecthk5.productservice.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Shoppers often type Vietnamese without diacritics; "ao thun" must find "Áo thun". */
@Testcontainers
@Tag("postgresql")
@SpringBootTest
@AutoConfigureMockMvc
class CatalogSearchPostgresTest {
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }

    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @MockBean SellerStoreClient stores;

    private final UUID store = UUID.randomUUID();

    @BeforeEach
    void products() {
        var category = UUID.randomUUID();
        db.update("INSERT INTO categories(id,name) VALUES (?,?)", category, "Thời trang " + category);
        for (String name : List.of("Áo thun cotton Đà Lạt", "Quần jean nữ", "Ấm đun nước")) {
            var product = UUID.randomUUID();
            db.update("""
                    INSERT INTO products(id,store_id,category_id,inventory_sku,name,unit_price,currency,status)
                    VALUES (?,?,?,?,?,100000,'VND','ACTIVE')
                    """, product, store, category, product.toString(), name);
        }
        when(stores.publicStores()).thenReturn(List.of(new SellerStoreClient.Facts(store, UUID.randomUUID(), "owner",
                "Tiệm thử " + store, "tiem-" + store, true, true, false)));
    }

    private String[] found(String q) throws Exception {
        var body = http.perform(get("/api/catalog/products").param("q", q).param("storeId", store.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return com.jayway.jsonpath.JsonPath.<List<String>>read(body, "$.items[*].name").toArray(String[]::new);
    }

    @Test
    void searchWithoutDiacriticsFindsVietnameseNames() throws Exception {
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[] {"Áo thun cotton Đà Lạt"}, found("ao thun"));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[] {"Áo thun cotton Đà Lạt"}, found("da lat"));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[] {"Quần jean nữ"}, found("QUAN JEAN"));
    }

    @Test
    void searchWithDiacriticsStillWorksAndDoesNotMatchOtherLetters() throws Exception {
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[] {"Áo thun cotton Đà Lạt"}, found("Áo thun"));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[] {"Ấm đun nước"}, found("am dun"));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[0], found("xyz"));
    }
}
