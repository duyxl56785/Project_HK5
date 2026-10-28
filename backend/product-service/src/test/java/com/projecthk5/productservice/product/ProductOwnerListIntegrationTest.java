package com.projecthk5.productservice.product;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.productservice.product.http.*;
import com.projecthk5.productservice.product.persistence.*;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import com.projecthk5.productservice.category.CategoryRepository;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real Product JPA/Flyway/PostgreSQL; principal and Store facts are TEST fixtures, never runtime authority proof. */
@SpringBootTest
@Import(ProductOwnerListIntegrationTest.Fixture.class)
@Testcontainers
@Tag("postgresql")
class ProductOwnerListIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16.6-alpine");
    static final UUID STORE=UUID.fromString("73000000-0000-4000-8000-000000000001");
    static final UUID OTHER=UUID.fromString("73000000-0000-4000-8000-000000000002");
    static final UUID CATEGORY=UUID.fromString("73000000-0000-4000-8000-000000000003");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);
        r.add("spring.datasource.password",PG::getPassword);r.add("spring.jpa.properties.hibernate.generate_statistics",()->true);
    }
    @Autowired ProductApplicationService application;
    @Autowired ProductPersistenceAdapter persistence;
    @Autowired ProductRepository repository;
    @Autowired CategoryRepository categories;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManagerFactory entityManagers;
    @Autowired FixturePolicy policy;
    @Autowired Inspector inspector;
    private int sequence;
    private ProductManagementHandler handler(String actor) {return new ProductManagementHandler(()->actor,application,new ProductManagementJson());}
    private ProductOwnerQuery query(String term,ProductStatus status,int page,int size) {return new ProductOwnerQuery(term,status,page,size);}
    private ProductManagementHandler.OwnedPage list(String actor,String term,ProductStatus status,int page,int size) {
        return handler(actor).listOwned(query(term,status,page,size));
    }
    @BeforeEach void reset() {
        inspector.beforeRows.set(null);policy.mode="allow";sequence=0;
        jdbc.update("DELETE FROM products");jdbc.update("UPDATE categories SET parent_id=NULL");jdbc.update("DELETE FROM categories");
        jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)",CATEGORY,"Owner list");inspector.sql.clear();
    }
    @Test void preparedHandlerListsAllSixStatesOnlyInsideEachActorsCurrentStore() {
        for(ProductStatus status:ProductStatus.values()) {
            create(STORE,"A-"+status,"a-"+status,status);create(OTHER,"B-"+status,"b-"+status,status);
        }
        var a=list("owner",null,null,0,10);var b=list("other",null,null,0,10);
        assertEquals(6,a.total());assertEquals(6,b.total());
        assertTrue(a.products().stream().allMatch(p->p.storeId().equals(STORE)));
        assertTrue(b.products().stream().allMatch(p->p.storeId().equals(OTHER)));
        assertEquals(Set.of(ProductStatus.values()),new HashSet<>(a.products().stream().map(ProductManagementResponse::status).toList()));
        for(ProductStatus status:ProductStatus.values()) {
            var page=list("owner",null,status,0,10);assertEquals(1,page.total());assertEquals(status,page.products().getFirst().status());
        }
    }
    @ParameterizedTest @ValueSource(strings={"unavailable","denied","null-scope","null-facts","wrong-scope","other-owner","closed","unapproved"})
    void unavailableOrIneligibleFreshScopeRejectsBeforeAnyProductSql(String mode) {
        create(STORE,"Private","private",ProductStatus.DRAFT);policy.mode=mode;
        var stats=entityManagers.unwrap(SessionFactory.class).getStatistics();stats.clear();inspector.sql.clear();
        var failure=assertThrows(ProductRuleException.class,()->list("owner",null,null,0,10));
        int expected=List.of("unavailable","null-scope","null-facts","wrong-scope").contains(mode)?503:403;
        assertEquals(expected,ProductManagementErrors.from(failure).status());
        assertEquals(0,stats.getPrepareStatementCount());assertTrue(inspector.sql.isEmpty());
    }
    @Test void absentHandlerAuthorityAndNullCriteriaCannotReachSql() {
        var stats=entityManagers.unwrap(SessionFactory.class).getStatistics();stats.clear();
        assertEquals(503,ProductManagementErrors.from(assertThrows(ProductRuleException.class,()->handler(null).listOwned(query(null,null,0,10)))).status());
        assertEquals(ProductRuleException.Code.INVALID_INPUT,assertThrows(ProductRuleException.class,()->handler("owner").listOwned(null)).getCode());
        assertEquals(0,stats.getPrepareStatementCount());
    }
    @Test void defaultApplicationAuthorityAndInvalidCriteriaExecuteNoProductSql() {
        var stats=entityManagers.unwrap(SessionFactory.class).getStatistics();stats.clear();
        var unconfigured=new ProductApplicationService(persistence,categories,
                new StaticListableBeanFactory().getBeanProvider(ProductAccessPolicy.class));
        assertEquals(503,ProductManagementErrors.from(assertThrows(ProductRuleException.class,
                ()->unconfigured.listOwned("owner",query(null,null,0,10)))).status());
        for(int[] pair:new int[][]{{-1,10},{0,0},{0,101},{Integer.MAX_VALUE,2}})
            assertEquals(ProductRuleException.Code.INVALID_INPUT,assertThrows(ProductRuleException.class,
                    ()->handler("owner").listOwned(query(null,null,pair[0],pair[1]))).getCode());
        for(String term:new String[]{"x".repeat(256),"a\0b","\ud800"})
            assertThrows(ProductRuleException.class,()->handler("owner").listOwned(query(term,null,0,10)));
        assertEquals(0,stats.getPrepareStatementCount());
    }
    @Test void authorityIsRecheckedOnEveryListAfterStoreCloses() {
        create(STORE,"Private","private",ProductStatus.DRAFT);assertEquals(1,list("owner",null,null,0,10).total());
        policy.mode="closed";var stats=entityManagers.unwrap(SessionFactory.class).getStatistics();stats.clear();
        assertEquals(403,ProductManagementErrors.from(assertThrows(ProductRuleException.class,
                ()->list("owner",null,null,0,10))).status());assertEquals(0,stats.getPrepareStatementCount());
    }
    @ParameterizedTest @ValueSource(strings={"%","_","\\","%' OR 1=1 --","x'); DROP TABLE products; --","áO đỎ","Sku-é-🚀","é"})
    void nameOrSkuSearchIsLiteralCaseInsensitiveUnicodeWithoutIdentityRewriting(String term) {
        var byName=create(STORE,"Match "+term+" end","name-match",ProductStatus.DRAFT);
        var bySku=create(STORE,"Unrelated","SKU "+term+" end",ProductStatus.REJECTED);
        create(STORE,"Other","unrelated",ProductStatus.ACTIVE);create(OTHER,"Match "+term,"foreign",ProductStatus.DRAFT);
        var page=list("owner",term.toUpperCase(Locale.ROOT),null,0,10);
        assertEquals(Set.of(byName.id(),bySku.id()),new HashSet<>(page.products().stream().map(ProductManagementResponse::id).toList()));
        assertEquals(2,page.total());assertEquals(4,repository.count());
        var status=list("owner",term,ProductStatus.REJECTED,0,10);assertEquals(1,status.total());assertEquals(bySku.id(),status.products().getFirst().id());
    }
    @Test void exactScalarFieldsCanonicalMoneyLongVersionAndNoPrivateFields() throws Exception {
        var p=create(STORE,"Áo Đỏ 🧥","Sku-é-🚀",ProductStatus.REJECTED);
        jdbc.update("UPDATE products SET unit_price=1000000000.0000,version=9007199254740993 WHERE id=?",p.id());
        var stats=entityManagers.unwrap(SessionFactory.class).getStatistics();stats.clear();inspector.sql.clear();
        var page=list("owner",null,null,0,100);var row=page.products().getFirst();
        assertEquals(p.id(),row.id());assertEquals(STORE,row.storeId());assertEquals(CATEGORY,row.categoryId());
        assertEquals(p.sku(),row.sku());assertEquals(p.name(),row.name());assertEquals("1000000000",row.unitPrice());
        assertEquals("VND",row.currency());assertEquals("9007199254740993",row.version());assertEquals(ProductStatus.REJECTED,row.status());
        assertEquals(2,stats.getPrepareStatementCount());assertEquals(0,stats.getEntityLoadCount());
        assertTrue(inspector.sql.stream().noneMatch(s->s.contains("review_reason")||s.contains("owner_reference")));
        String serialized=new ObjectMapper().writeValueAsString(page);
        assertFalse(serialized.contains("reviewReason"));assertFalse(serialized.contains("ownerReference"));assertFalse(serialized.contains("private reason"));
        assertThrows(UnsupportedOperationException.class,()->page.products().clear());
        var raw=application.listOwned("owner",query(null,null,0,100));assertEquals(new BigDecimal("1000000000.0000"),raw.products().getFirst().unitPrice());
        assertThrows(UnsupportedOperationException.class,()->raw.products().clear());
    }
    @Test void nameThenUuidOrderingHasStableBoundariesAndCorrectEmptyTotals() {
        var expected=new ArrayList<UUID>();for(int i=0;i<7;i++)expected.add(create(STORE,"Same","sku-"+i,ProductStatus.DRAFT).id());
        for(int repeat=0;repeat<2;repeat++) {
            var observed=new ArrayList<UUID>();
            for(int page=0;page<4;page++) {
                var result=list("owner",null,null,page,3);assertEquals(7,result.total());assertEquals(page,result.page());assertEquals(3,result.pageSize());
                assertEquals(page<2?3:page==2?1:0,result.products().size());observed.addAll(result.products().stream().map(ProductManagementResponse::id).toList());
            }assertEquals(expected,observed);
        }
        var first=create(STORE,"A before Same","first",ProductStatus.DRAFT);assertEquals(first.id(),list("owner",null,null,0,1).products().getFirst().id());
        var empty=list("owner","missing",null,0,10);assertEquals(0,empty.total());assertTrue(empty.products().isEmpty());
        var far=list("owner",null,null,Integer.MAX_VALUE,1);assertEquals(8,far.total());assertTrue(far.products().isEmpty());
    }
    @Test void maximumPageUsesOnlyCountAndScalarRowsWithoutNPlusOne() {
        for(int i=0;i<101;i++)create(STORE,"Item-"+i,"sku-"+i,ProductStatus.DRAFT);
        var stats=entityManagers.unwrap(SessionFactory.class).getStatistics();stats.clear();
        var page=list("owner",null,null,0,100);assertEquals(101,page.total());assertEquals(100,page.products().size());
        assertEquals(2,stats.getPrepareStatementCount());assertEquals(0,stats.getEntityLoadCount());
    }
    @ParameterizedTest @ValueSource(strings={"delete","status","name","price"})
    void countAndRowsShareReadOnlyRepeatableReadSnapshotDuringConcurrentWrites(String mutation) {
        create(STORE,"Keep first","first",ProductStatus.DRAFT);var victim=create(STORE,"Keep victim","victim",ProductStatus.DRAFT);
        inspector.beforeRows.set(()->{
            assertEquals("repeatable read",jdbc.queryForObject("SHOW transaction_isolation",String.class));
            assertEquals("on",jdbc.queryForObject("SHOW transaction_read_only",String.class));
            var tx=new TransactionTemplate(transactions);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(s->{
                String sql=switch(mutation) {
                    case "delete" -> "DELETE FROM products WHERE id=?";
                    case "status" -> "UPDATE products SET status='ACTIVE',version=version+1 WHERE id=?";
                    case "name" -> "UPDATE products SET name='Gone',version=version+1 WHERE id=?";
                    default -> "UPDATE products SET unit_price=99,version=version+1 WHERE id=?";
                };jdbc.update(sql,victim.id());
            });
        });
        var page=list("owner","Keep",ProductStatus.DRAFT,0,10);assertNull(inspector.beforeRows.get(),"Mutation must execute between count and rows");
        assertEquals(2,page.total());assertEquals(2,page.products().size());
        var before=page.products().stream().filter(p->p.id().equals(victim.id())).findFirst().orElseThrow();assertEquals("10",before.unitPrice());assertEquals("0",before.version());
        var next=list("owner","Keep",ProductStatus.DRAFT,0,10);assertEquals(mutation.equals("price")?2:1,next.total());
        if(mutation.equals("price"))assertEquals("99",next.products().stream().filter(p->p.id().equals(victim.id())).findFirst().orElseThrow().unitPrice());
    }
    @ParameterizedTest @ValueSource(strings={"default","read-committed"})
    void insufficientAmbientIsolationFailsWithoutPublishingCountOrRows(String mode) {
        var tx=new TransactionTemplate(transactions);if(mode.equals("read-committed"))tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var stats=entityManagers.unwrap(SessionFactory.class).getStatistics();stats.clear();
        tx.executeWithoutResult(s->{assertEquals(ProductPersistenceException.Code.UNAVAILABLE,assertThrows(ProductPersistenceException.class,
                ()->list("owner",null,null,0,10)).getCode());s.setRollbackOnly();});assertEquals(0,stats.getPrepareStatementCount());
    }
    @Test void serializableReadOnlyAmbientSnapshotIsAccepted() {
        create(STORE,"One","one",ProductStatus.DRAFT);var tx=new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);tx.setReadOnly(true);tx.executeWithoutResult(s->assertEquals(1,list("owner",null,null,0,10).total()));
    }
    @Test void actualCountLockTimeoutReturnsUnavailableWithoutPartialPage() throws Exception {
        create(STORE,"One","one",ProductStatus.DRAFT);
        try(var blocker=DriverManager.getConnection(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())) {
            blocker.setAutoCommit(false);
            try {
                blocker.createStatement().execute("LOCK TABLE products IN ACCESS EXCLUSIVE MODE");
                var tx=new TransactionTemplate(transactions);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);tx.setReadOnly(true);
                tx.executeWithoutResult(s->{
                    jdbc.execute("SET LOCAL lock_timeout='150ms'");
                    var stats=entityManagers.unwrap(SessionFactory.class).getStatistics();stats.clear();
                    assertEquals(503,ProductManagementErrors.from(assertThrows(ProductPersistenceException.class,
                            ()->list("owner",null,null,0,10))).status());
                    assertEquals(1,stats.getPrepareStatementCount(),"Must execute a genuinely failing Product query");s.setRollbackOnly();
                });
            } finally {blocker.rollback();}
        }
        assertEquals(1,list("owner",null,null,0,10).total());
    }
    ProductListing create(UUID store,String name,String sku,ProductStatus status) {
        UUID id=UUID.fromString("00000000-0000-4000-8000-"+String.format("%012d",++sequence));
        var listing=new ProductListing(id,store,CATEGORY,sku,name,new BigDecimal("10.0000"),"VND",status,
                status==ProductStatus.REJECTED||status==ProductStatus.SUSPENDED?"private reason":null);return persistence.create(listing).listing();
    }
    @TestConfiguration static class Fixture {
        @Bean FixturePolicy policy() {return new FixturePolicy();}
        @Bean Inspector inspector() {return new Inspector();}
        @Bean HibernatePropertiesCustomizer inspection(Inspector inspector) {return p->p.put("hibernate.session_factory.statement_inspector",inspector);}
    }
    static class FixturePolicy implements ProductAccessPolicy {
        String mode="allow";
        public UUID currentManagementStore(String actor) {
            if(mode.equals("unavailable"))throw new ProductRuleException(ProductRuleException.Code.AUTHORIZATION_UNAVAILABLE,"Fixture unavailable");
            if(mode.equals("denied"))throw new ProductRuleException(ProductRuleException.Code.ACCESS_DENIED,"Fixture denied");
            return mode.equals("null-scope")?null:actor.equals("owner")?STORE:OTHER;
        }
        public StoreAccess requireSellerManagement(String actor,UUID store) {
            if(mode.equals("null-facts"))return null;
            return new StoreAccess(mode.equals("wrong-scope")?UUID.randomUUID():store,UUID.randomUUID(),mode.equals("other-owner")?"foreign":actor,!mode.equals("unapproved"),!mode.equals("closed"),false);
        }
        public StoreAccess requireModeration(String actor,UUID store) {throw new UnsupportedOperationException();}
        public StoreAccess readStore(UUID store) {throw new UnsupportedOperationException();}
    }
    static class Inspector implements StatementInspector {
        final AtomicReference<Runnable> beforeRows=new AtomicReference<>();final List<String> sql=new ArrayList<>();
        public String inspect(String statement) {
            var lower=statement.toLowerCase(Locale.ROOT);sql.add(lower);
            if(lower.startsWith("select")&&lower.contains("from products")&&!lower.contains("count(")) {var hook=beforeRows.getAndSet(null);if(hook!=null)hook.run();}
            return statement;
        }
    }
}
