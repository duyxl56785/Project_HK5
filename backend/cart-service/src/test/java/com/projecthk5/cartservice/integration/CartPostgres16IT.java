package com.projecthk5.cartservice.integration;

import com.projecthk5.cartservice.entity.Cart;
import com.projecthk5.cartservice.entity.CartItem;
import com.projecthk5.cartservice.repository.CartRepository;
import com.projecthk5.cartservice.service.CartService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest
class CartPostgres16IT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",PG::getJdbcUrl);
        r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);
        r.add("app.security.issuer",()->"https://test-issuer.example.invalid");
        r.add("app.security.audience",()->"ecommerce");
        r.add("app.security.current-kid",()->"test-kid");
        r.add("app.security.current-key-b64",()->"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    @Autowired CartRepository carts;
    @Autowired CartService service;
    @Autowired JdbcTemplate sql;

    @Test void migrationHibernateValidateCascadeTwoStoresAndUniqueProduct() {
        Cart cart=new Cart("cart-test-"+UUID.randomUUID());
        cart.getItems().add(new CartItem(cart,"p1","store1","seller1",2,new BigDecimal("10000")));
        cart.getItems().add(new CartItem(cart,"p2","store2","seller2",1,new BigDecimal("15000")));
        Cart saved=carts.saveAndFlush(cart);
        assertEquals(2,sql.queryForObject("select count(*) from cart_items where cart_id=?",Integer.class,saved.getId()));
        assertEquals(2,sql.queryForObject("select count(distinct store_id) from cart_items where cart_id=?",Integer.class,saved.getId()));
        assertThrows(DataIntegrityViolationException.class,()->sql.update(
            "insert into cart_items(id,cart_id,product_id,store_id,seller_id,quantity,unit_price_snapshot) values(?,?,?,?,?,?,?)",
            UUID.randomUUID(),saved.getId(),"p1","store1","seller1",1,new BigDecimal("10000")));
        carts.deleteById(saved.getId());
        carts.flush();
        assertEquals(0,sql.queryForObject("select count(*) from cart_items where cart_id=?",Integer.class,saved.getId()));
    }

    @Test void c2CartRecoveryIsBoundToOwnerClaimAndExactRevision() {
        var cart = new Cart("cart-recovery-" + UUID.randomUUID());
        cart.getItems().add(new CartItem(cart, "p", "store", "seller", 1, new BigDecimal("10000")));
        cart = carts.saveAndFlush(cart);
        String customer = cart.getCustomerId();
        long revision = service.claim(customer, "first-checkout", cart.getRevision()).revision();
        service.recoverCheckout("another-customer", "first-checkout", revision, true);
        service.recoverCheckout(customer, "wrong-key", revision, true);
        service.recoverCheckout(customer, "first-checkout", revision - 1, true);
        assertTrue(service.view(customer).claimed());
        assertEquals(1, service.view(customer).items().size());
        service.recoverCheckout(customer, "first-checkout", revision, false);
        var reopened = service.view(customer);
        assertFalse(reopened.claimed());
        assertEquals(1, reopened.items().size());
        long nextRevision = service.claim(customer, "next-checkout", reopened.revision()).revision();
        service.recoverCheckout(customer, "first-checkout", revision, true);
        assertTrue(service.view(customer).claimed());
        service.recoverCheckout(customer, "next-checkout", nextRevision, true);
        service.recoverCheckout(customer, "next-checkout", nextRevision, true);
        assertFalse(service.view(customer).claimed());
        assertTrue(service.view(customer).items().isEmpty());
    }
}
