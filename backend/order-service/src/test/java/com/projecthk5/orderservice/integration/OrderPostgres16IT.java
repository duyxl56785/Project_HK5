package com.projecthk5.orderservice.integration;

import com.projecthk5.orderservice.entity.*;
import com.projecthk5.orderservice.repository.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
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
class OrderPostgres16IT {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",PG::getJdbcUrl);
        r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);
        r.add("app.security.issuer",()->"https://test-issuer.example.invalid");
        r.add("app.security.audience",()->"ecommerce");
        r.add("app.security.current-kid",()->"test-kid");
        r.add("app.security.current-key-b64",()->"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
    @Autowired CheckoutAttemptRepository attempts;
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate sql;

    @Test void realFlywayValidateTwoSellerOrdersCascadeAndUniqueCustomerKey() {
        String customer="order-test-"+UUID.randomUUID();
        CheckoutAttempt a=attempts.saveAndFlush(new CheckoutAttempt(customer,"key","fingerprint"));
        assertThrows(DataIntegrityViolationException.class,()->attempts.saveAndFlush(
            new CheckoutAttempt(customer,"key","fingerprint")));
        Order o=new Order(a.getId(),customer);
        SellerOrder a1=new SellerOrder(o,"seller1","store1",new BigDecimal("20000"),new BigDecimal("1000"));
        SellerOrder a2=new SellerOrder(o,"seller2","store2",new BigDecimal("15000"),BigDecimal.ZERO);
        a1.getItems().add(new OrderItem(a1,"p1",2,new BigDecimal("10000"),new BigDecimal("20000")));
        a2.getItems().add(new OrderItem(a2,"p2",1,new BigDecimal("15000"),new BigDecimal("15000")));
        o.addSellerOrder(a1);o.addSellerOrder(a2);
        orders.saveAndFlush(o);
        assertEquals(2,sql.queryForObject("select count(*) from seller_orders where order_id=?",Integer.class,o.getId()));
        assertEquals(2,sql.queryForObject("select count(*) from order_items oi join seller_orders so on so.id=oi.seller_order_id where so.order_id=?",Integer.class,o.getId()));
    }

    @Test void concurrentSameKeyOnlyOneRecordCanCommit() throws Exception {
        String customer="concurrent-"+UUID.randomUUID();
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Boolean> create=()->{
                start.await();
                try {attempts.saveAndFlush(new CheckoutAttempt(customer,"shared-key","same-fingerprint"));return true;}
                catch(DataIntegrityViolationException e){return false;}
            };
            Future<Boolean> a=pool.submit(create), b=pool.submit(create);
            start.countDown();
            assertNotEquals(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            assertEquals(1,sql.queryForObject(
                "select count(*) from checkout_attempts where customer_id=? and idempotency_key=?",
                Integer.class,customer,"shared-key"));
        } finally { pool.shutdownNow(); }
    }
}
