package com.projecthk5.cartservice.integration;

import com.projecthk5.cartservice.entity.Cart;
import com.projecthk5.cartservice.entity.CartItem;
import com.projecthk5.cartservice.repository.CartRepository;
import com.projecthk5.cartservice.service.CartService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

/** Independent review probe; real Cart service/JPA/PostgreSQL, no mocked dependency or production transport. */
@Tag("postgres-integration") @Testcontainers @SpringBootTest
class CartClearOwnerPostgresIT {
 @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
  r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);r.add("spring.datasource.password",PG::getPassword);
  r.add("app.security.issuer",()->"https://probe.example.invalid");r.add("app.security.audience",()->"probe");
  r.add("app.security.current-kid",()->"probe-kid");r.add("app.security.current-key-b64",()->"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
 }
 @Autowired CartRepository carts; @Autowired CartService service; @Autowired JdbcTemplate sql; @Autowired PlatformTransactionManager tm;
 String seed() {
  String owner="probe-"+UUID.randomUUID();Cart c=new Cart(owner);
  c.getItems().add(new CartItem(c,"p1","s1","seller1",2,new BigDecimal("10000")));
  c.getItems().add(new CartItem(c,"p2","s2","seller2",3,new BigDecimal("20000")));
  carts.saveAndFlush(c);return owner;
 }
 int rows(String owner) {return sql.queryForObject("select count(*) from cart_items i join carts c on c.id=i.cart_id where c.customer_id=?",Integer.class,owner);}
 @Test void clearOnlyOneCustomerAndPhysicallyRemovesOnlyItsItems() {
  String a=seed(),b=seed();long bRevision=service.view(b).revision();
  assertEquals(2,rows(a));assertEquals(2,rows(b));assertTrue(service.clear(a).items().isEmpty());
  assertEquals(0,rows(a));assertEquals(2,rows(b));assertEquals(bRevision,service.view(b).revision());
 }
 @Test void clearOfClaimedCartRollsBackAndRetainsEveryCheckoutFact() {
  String a=seed();service.claim(a,"claim",null);var before=service.view(a);
  assertThrows(IllegalStateException.class,()->service.clear(a));var after=service.view(a);
  assertEquals(before,after);assertEquals(2,rows(a));
  assertEquals("claim",sql.queryForObject("select checkout_claim from carts where customer_id=?",String.class,a));
 }
 @Test void absentCartAndRepeatedClearAreSafeAndDoNotTouchOtherCustomer() {
  String a=seed();assertTrue(service.clear("absent-"+UUID.randomUUID()).items().isEmpty());assertEquals(2,rows(a));
  assertTrue(service.clear(a).items().isEmpty());assertTrue(service.clear(a).items().isEmpty());assertEquals(0,rows(a));
 }
 @Test void concurrentClearWaitsForRealPostgresClaimLockThenRefuses() throws Exception {
  String owner=seed();CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1),clearing=new CountDownLatch(1);
  ExecutorService pool=Executors.newFixedThreadPool(2);
  Future<?> claimant=pool.submit(()->new TransactionTemplate(tm).executeWithoutResult(tx->{
   service.claim(owner,"concurrent-claim",null);held.countDown();
   try {assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}
  }));
  try {
   assertTrue(held.await(10,TimeUnit.SECONDS));
   Future<?> clear=pool.submit(()->{clearing.countDown();return service.clear(owner);});assertTrue(clearing.await(5,TimeUnit.SECONDS));
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean blocked=false;
   while(System.nanoTime()<deadline) {
    if(sql.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and cardinality(pg_blocking_pids(pid))>0",Integer.class)>0){blocked=true;break;}
    Thread.sleep(20);
   }
   assertTrue(blocked,"Must observe an actual PostgreSQL lock waiter, not merely queued threads");assertFalse(clear.isDone());
   release.countDown();claimant.get(5,TimeUnit.SECONDS);
   ExecutionException refused=assertThrows(ExecutionException.class,()->clear.get(5,TimeUnit.SECONDS));assertInstanceOf(IllegalStateException.class,refused.getCause());
   assertTrue(service.view(owner).claimed());assertEquals(2,rows(owner));
  }finally{release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
 }
}
