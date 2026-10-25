package com.projecthk5.cartservice.finance;

import com.projecthk5.cartservice.api.CartDtos.AddItem;
import com.projecthk5.cartservice.catalog.CartCatalogPort;
import com.projecthk5.cartservice.entity.Cart;
import com.projecthk5.cartservice.entity.CartItem;
import com.projecthk5.cartservice.repository.CartRepository;
import com.projecthk5.cartservice.service.CartService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Real schema/service storage regression; Catalog is a fixture, not a live provider. */
@Tag("postgres-integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CartService.class)
class CartMoneyStoragePostgresRegressionTest {
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", PG::getJdbcUrl);
        properties.add("spring.datasource.username", PG::getUsername);
        properties.add("spring.datasource.password", PG::getPassword);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired CartService service;
    @Autowired CartRepository carts;
    @Autowired JdbcTemplate sql;
    @MockBean CartCatalogPort catalog;

    @Test
    void postgresWouldSilentlyRoundTheOriginalUnsafePrice() {
        BigDecimal stored = sql.queryForObject("select cast(? as numeric(19,2))",
                BigDecimal.class, new BigDecimal("1.005"));
        assertEquals(new BigDecimal("1.01"), stored);
        assertNotEquals(0, new BigDecimal("1.005").compareTo(stored));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.001", "1.005", "100000000000000000.00"})
    void invalidCatalogPriceDoesNotCreateACart(String price) {
        String customer = "tv5-new-" + UUID.randomUUID();
        catalogPrice(price);
        assertThrows(IllegalArgumentException.class,
                () -> service.add(customer, new AddItem("new-product", 1)));
        assertEquals(0, sql.queryForObject("select count(*) from carts where customer_id=?",
                Integer.class, customer));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.001", "1.005", "100000000000000000.00"})
    void invalidCatalogPricePreservesExistingItemsAndRevision(String price) {
        String customer = "tv5-existing-" + UUID.randomUUID();
        Cart cart = new Cart(customer);
        cart.getItems().add(new CartItem(cart, "original", "store", "seller", 2,
                new BigDecimal("3.25")));
        Cart saved = carts.saveAndFlush(cart);
        long revision = saved.getRevision();
        catalogPrice(price);
        assertThrows(IllegalArgumentException.class,
                () -> service.add(customer, new AddItem("new-product", 1)));
        assertEquals(revision, sql.queryForObject("select revision from carts where id=?",
                Long.class, saved.getId()).longValue());
        assertEquals(1, sql.queryForObject("select count(*) from cart_items where cart_id=?",
                Integer.class, saved.getId()));
        assertEquals(2, sql.queryForObject("select quantity from cart_items where cart_id=?",
                Integer.class, saved.getId()));
        assertEquals(new BigDecimal("3.25"), sql.queryForObject(
                "select unit_price_snapshot from cart_items where cart_id=?",
                BigDecimal.class, saved.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1.2300", "99999999999999999.99"})
    void exactCurrentSchemaBoundariesRoundTripWithoutValueChange(String price) {
        String customer = "tv5-exact-" + UUID.randomUUID();
        catalogPrice(price);
        service.add(customer, new AddItem("new-product", 1));
        BigDecimal stored = sql.queryForObject(
                "select i.unit_price_snapshot from cart_items i join carts c on c.id=i.cart_id"
                        + " where c.customer_id=?", BigDecimal.class, customer);
        assertEquals(0, new BigDecimal(price).compareTo(stored));
        assertEquals(2, stored.scale());
    }

    private void catalogPrice(String price) {
        when(catalog.resolve("new-product")).thenReturn(new CartCatalogPort.ProductSnapshot(
                "new-product", "store", "seller", true, new BigDecimal(price)));
    }
}
