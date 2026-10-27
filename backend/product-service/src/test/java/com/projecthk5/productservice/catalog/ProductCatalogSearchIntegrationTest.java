package com.projecthk5.productservice.catalog;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.productservice.catalog.CatalogCandidatePage.Candidate;
import com.projecthk5.productservice.catalog.CatalogSearchQuery.Sort;
import com.projecthk5.productservice.catalog.CatalogSnapshotSource.*;
import com.projecthk5.productservice.catalog.CatalogBatchValidator.*;
import com.projecthk5.productservice.product.*;
import com.projecthk5.productservice.product.persistence.*;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Production Product JPA/Flyway/PG. Candidate counts are never claims of public eligibility or live authority. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ProductCatalogSearchIntegrationTest.InspectionFixture.class)
@Testcontainers
@Tag("postgresql")
class ProductCatalogSearchIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final UUID CATEGORY_A = UUID.fromString("90000000-0000-4000-8000-000000000001");
    private static final UUID CATEGORY_B = UUID.fromString("90000000-0000-4000-8000-000000000002");
    private static final UUID STORE_A = UUID.fromString("90000000-0000-4000-8000-000000000003");
    private static final UUID STORE_B = UUID.fromString("90000000-0000-4000-8000-000000000004");
    private static final UUID SELLER_A = UUID.fromString("90000000-0000-4000-8000-000000000005");
    private static final UUID SELLER_B = UUID.fromString("90000000-0000-4000-8000-000000000006");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",PG::getJdbcUrl);
        registry.add("spring.datasource.username",PG::getUsername);
        registry.add("spring.datasource.password",PG::getPassword);
        registry.add("spring.jpa.properties.hibernate.generate_statistics",()->true);
    }
    @Autowired ProductRepository products;
    @Autowired ProductPersistenceAdapter persistence;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagers;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationContext context;
    @Autowired ObservationInspector inspector;
    @Autowired MockMvc http;
    private int sequence;

    @BeforeEach void resetDisposableDatabase() {
        inspector.beforeRows.set(null);
        inspector.sql.clear();
        jdbc.update("DELETE FROM products");
        jdbc.update("UPDATE categories SET parent_id=NULL");
        jdbc.update("DELETE FROM categories");
        jdbc.update("INSERT INTO categories(id,name) VALUES (?,?),(?,?)",CATEGORY_A,"A",CATEGORY_B,"B");
        sequence=0;
    }

    @Test void nameSearchIsLiteralCaseInsensitiveAndPreservesExactProjection() {
        var p=create("Red Shirt",CATEGORY_A,"10.0000",ProductStatus.ACTIVE,STORE_A,"Exact-SkU");
        create("Blue Shirt",CATEGORY_A,"20",ProductStatus.ACTIVE);
        var page=products.searchCandidates(query("rEd sHi",null,null,null,null,0,10));
        assertEquals(1,page.candidateTotal());
        Candidate c=page.candidates().getFirst();
        assertEquals(p.id(),c.productId());assertEquals(p.storeId(),c.storeId());assertEquals(p.categoryId(),c.categoryId());
        assertEquals("Exact-SkU",c.inventorySku());assertEquals(p.name(),c.name());
        assertEquals(new BigDecimal("10.0000"),c.unitPrice());assertEquals("VND",c.currency());
    }

    @ParameterizedTest @ValueSource(strings={"%","_","\\","%' OR 1=1 --","x'); DROP TABLE products; --"})
    void searchWildcardsQuotesAndSqlInjectionAreLiteralData(String term) {
        var exact=create("Match "+term+" end",CATEGORY_A,"10",ProductStatus.ACTIVE);
        create("Unrelated item",CATEGORY_A,"20",ProductStatus.ACTIVE);
        var page=products.searchCandidates(query(term,null,null,null,null,0,10));
        assertEquals(List.of(exact.id()),ids(page));assertEquals(1,page.candidateTotal());
        assertEquals(2,products.count());
    }

    @Test void categoryAndCombinedFiltersMatchRowsAndCounts() {
        var a=create("Wanted",CATEGORY_A,"20",ProductStatus.ACTIVE);
        create("Wanted",CATEGORY_B,"20",ProductStatus.ACTIVE);
        create("Wanted",CATEGORY_A,"30",ProductStatus.ACTIVE);
        create("Other",CATEGORY_A,"20",ProductStatus.ACTIVE);
        var page=products.searchCandidates(query("want",CATEGORY_A,"10","25",null,0,10));
        assertEquals(List.of(a.id()),ids(page));assertEquals(1,page.candidateTotal());
        assertEquals(0,products.searchCandidates(query(null,UUID.randomUUID(),null,null,null,0,10)).candidateTotal());
    }

    @Test void priceBoundsAreInclusiveExplicitWholeVndWithoutRounding() {
        var low=create("Low",CATEGORY_A,"1",ProductStatus.ACTIVE);
        var middle=create("Middle",CATEGORY_A,"10.0000",ProductStatus.ACTIVE);
        var high=create("High",CATEGORY_A,"20",ProductStatus.ACTIVE);
        create("Cap",CATEGORY_A,"1000000000",ProductStatus.ACTIVE);
        assertEquals(Set.of(middle.id(),high.id()),new HashSet<>(ids(products.searchCandidates(query(null,null,"10","20",null,0,10)))));
        assertEquals(3,products.searchCandidates(query(null,null,"10",null,null,0,10)).candidateTotal());
        assertEquals(3,products.searchCandidates(query(null,null,null,"20",null,0,10)).candidateTotal());
        assertEquals(List.of(low.id()),ids(products.searchCandidates(query(null,null,"1","1",null,0,10))));
    }

    @ParameterizedTest @EnumSource(Sort.class)
    void deterministicSortHasUuidTieBreakAcrossRepeatedPages(Sort sort) {
        var a=create("Same",CATEGORY_A,"20",ProductStatus.ACTIVE);
        var b=create("Same",CATEGORY_A,"10",ProductStatus.ACTIVE);
        var c=create("Same",CATEGORY_A,"10",ProductStatus.ACTIVE);
        var expected=switch(sort) {
            case NAME_ASC -> List.of(a.id(),b.id(),c.id());
            case PRICE_ASC -> List.of(b.id(),c.id(),a.id());
            case PRICE_DESC -> List.of(a.id(),b.id(),c.id());
        };
        for (int repeat=0;repeat<2;repeat++) {
            var collected=new ArrayList<UUID>();
            for(int page=0;page<3;page++) {
                var result=products.searchCandidates(query(null,null,null,null,sort,page,1));
                assertEquals(3,result.candidateTotal());collected.addAll(ids(result));
            }
            assertEquals(expected,collected);
        }
    }

    @Test void paginationFirstMiddleLastBeyondAndEmptyHaveCandidateCountsOnly() {
        IntStream.range(0,7).forEach(i->create("Item-"+i,CATEGORY_A,"10",ProductStatus.ACTIVE));
        for(int page=0;page<4;page++) {
            var result=products.searchCandidates(query(null,null,null,null,null,page,3));
            assertEquals(7,result.candidateTotal());assertEquals(page,result.page());assertEquals(3,result.pageSize());
            assertEquals(page<2?3:page==2?1:0,result.candidates().size());
        }
        var empty=products.searchCandidates(query("missing",null,null,null,null,0,3));
        assertEquals(0,empty.candidateTotal());assertTrue(empty.candidates().isEmpty());
        var distant=products.searchCandidates(query(null,null,null,null,null,Integer.MAX_VALUE,1));
        assertEquals(7,distant.candidateTotal());assertTrue(distant.candidates().isEmpty());
    }

    @Test void maximumPageSizeUsesTwoScalarQueriesAndNoEntityOrCategoryNPlusOne() {
        IntStream.range(0,101).forEach(i->create("Item-"+String.format("%03d",i),CATEGORY_A,"10",ProductStatus.ACTIVE));
        var stats=entityManagers.unwrap(SessionFactory.class).getStatistics();stats.clear();inspector.sql.clear();
        var result=products.searchCandidates(query(null,null,null,null,null,0,100));
        assertEquals(100,result.candidates().size());assertEquals(101,result.candidateTotal());
        assertEquals(2,stats.getPrepareStatementCount());assertEquals(0,stats.getEntityLoadCount());
        assertTrue(inspector.sql.stream().noneMatch(s->s.contains("review_reason")||s.contains("owner_reference")));
    }

    @ParameterizedTest @EnumSource(ProductStatus.class)
    void activeIsTheOnlyCandidateStateAndCannotBeOverriddenByCaller(ProductStatus status) {
        var p=create("State",CATEGORY_A,"10",status);
        var result=products.searchCandidates(query(null,null,null,null,null,0,10));
        assertEquals(status==ProductStatus.ACTIVE?List.of(p.id()):List.of(),ids(result));
        assertEquals(status==ProductStatus.ACTIVE?1:0,result.candidateTotal());
    }

    @Test void unicodeNamesAndCaseNfcNfdAndSupplementarySkuArePreserved() {
        var a=create("Áo Đỏ 🧥",CATEGORY_A,"10",ProductStatus.ACTIVE,STORE_A,"Sku-\u00e9-\ud83d\ude80");
        var b=create("E\u0301clair",CATEGORY_A,"20",ProductStatus.ACTIVE,STORE_A,"sku-e\u0301");
        var first=products.searchCandidates(query("áO đỎ",null,null,null,null,0,10));
        assertEquals(List.of(a.id()),ids(first));assertEquals(a.sku(),first.candidates().getFirst().inventorySku());
        var second=products.searchCandidates(query("E\u0301",null,null,null,null,0,10));
        assertEquals(List.of(b.id()),ids(second));assertEquals(b.name(),second.candidates().getFirst().name());
        assertEquals(b.sku(),second.candidates().getFirst().inventorySku());
    }

    @Test void privateFieldsAreAbsentAndResultsImmutable() throws Exception {
        create("Public candidate",CATEGORY_A,"10",ProductStatus.ACTIVE);
        create("Private rejected",CATEGORY_A,"10",ProductStatus.REJECTED);
        var page=products.searchCandidates(query(null,null,null,null,null,0,10));
        var serialized=new ObjectMapper().writeValueAsString(page);
        assertFalse(serialized.contains("ownerReference"));assertFalse(serialized.contains("reviewReason"));
        assertFalse(serialized.contains("private moderation reason"));assertFalse(serialized.contains("Private rejected"));
        assertThrows(UnsupportedOperationException.class,()->page.candidates().clear());
        assertTrue(serialized.contains("candidateTotal"));assertFalse(serialized.contains("eligibleTotal"));
    }

    @Test void normalCategoryDeleteRemainsRestrictedAndInjectedMissingCategoryIsExcluded() {
        create("Product",CATEGORY_A,"10",ProductStatus.ACTIVE);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM categories WHERE id=?",CATEGORY_A));
        assertEquals(1,products.searchCandidates(query(null,null,null,null,null,0,10)).candidateTotal());
        requiresNew().executeWithoutResult(tx-> {
            // Disposable-DB corruption only; normal operations cannot bypass the unchanged FK.
            jdbc.execute("SET LOCAL session_replication_role=replica");
            jdbc.update("DELETE FROM categories WHERE id=?",CATEGORY_A);
        });
        var page=products.searchCandidates(query(null,null,null,null,null,0,10));
        assertEquals(0,page.candidateTotal());assertTrue(page.candidates().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings={"price","status","category","delete"})
    void concurrentUpdatesBetweenCountAndRowsShareOneLocalSnapshot(String mutation) {
        create("First",CATEGORY_A,"10",ProductStatus.ACTIVE);
        var victim=create("Victim",CATEGORY_A,"20",ProductStatus.ACTIVE);
        AtomicInteger writes=new AtomicInteger();
        inspector.beforeRows.set(()-> {
            assertEquals("repeatable read",jdbc.queryForObject("SHOW transaction_isolation",String.class));
            assertEquals("on",jdbc.queryForObject("SHOW transaction_read_only",String.class));
            requiresNew().executeWithoutResult(tx-> {
            String sql=switch(mutation) {
                case "price" -> "UPDATE products SET unit_price=200,version=version+1 WHERE id=?";
                case "status" -> "UPDATE products SET status='INACTIVE',version=version+1 WHERE id=?";
                case "category" -> "UPDATE products SET category_id=?,version=version+1 WHERE id=?";
                default -> "DELETE FROM products WHERE id=?";
            };
            if (mutation.equals("category")) jdbc.update(sql,CATEGORY_B,victim.id());
            else jdbc.update(sql,victim.id());
            writes.incrementAndGet();
            });
        });
        var filter=query(null,CATEGORY_A,null,"100",null,0,10);
        var observed=products.searchCandidates(filter);
        assertEquals(1,writes.get());assertEquals(2,observed.candidateTotal());assertEquals(2,observed.candidates().size());
        assertEquals(new BigDecimal("20"),observed.candidates().stream().filter(c->c.productId().equals(victim.id())).findFirst().orElseThrow().unitPrice());
        var next=products.searchCandidates(filter);
        assertEquals(1,next.candidateTotal());assertEquals(1,next.candidates().size());
    }

    @ParameterizedTest @ValueSource(strings={"default","read-committed"})
    void incompatibleAmbientTransactionsCannotPublishInconsistentLocalTotals(String isolation) {
        var tx=new TransactionTemplate(transactions);
        if(isolation.equals("read-committed"))tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.executeWithoutResult(status-> {
            var error=assertThrows(ProductPersistenceException.class,()->products.searchCandidates(query(null,null,null,null,null,0,10)));
            assertEquals(ProductPersistenceException.Code.UNAVAILABLE,error.getCode());status.setRollbackOnly();
        });
    }

    @Test void actualPostgresCountTimeoutReturnsUnavailableWithoutPartialPage() throws Exception {
        create("Product",CATEGORY_A,"10",ProductStatus.ACTIVE);
        try (Connection blocker=DriverManager.getConnection(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())) {
            blocker.setAutoCommit(false);
            try {
                blocker.createStatement().execute("LOCK TABLE products IN ACCESS EXCLUSIVE MODE");
                var tx=new TransactionTemplate(transactions);
                tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                tx.executeWithoutResult(status-> {
                    jdbc.execute("SET LOCAL lock_timeout='150ms'");
                    var stats=entityManagers.unwrap(SessionFactory.class).getStatistics();stats.clear();
                    var error=assertThrows(ProductPersistenceException.class,
                            ()->products.searchCandidates(query(null,null,null,null,null,0,10)));
                    assertEquals(ProductPersistenceException.Code.UNAVAILABLE,error.getCode());
                    assertEquals(1,stats.getPrepareStatementCount(),"Must exercise an actual failing Product SQL query");
                    status.setRollbackOnly();
                });
            } finally {blocker.rollback();}
        }
        assertEquals(1,products.searchCandidates(query(null,null,null,null,null,0,10)).candidateTotal());
    }

    @Test void invalidInputsDoNotExecuteProductQueries() {
        var stats=entityManagers.unwrap(SessionFactory.class).getStatistics();stats.clear();
        assertThrows(ProductRuleException.class,()->products.searchCandidates(null));
        assertThrows(ProductRuleException.class,()->products.searchCandidates(query(null,null,"1.5",null,null,0,10)));
        assertThrows(ProductRuleException.class,()->products.searchCandidates(query(null,null,null,null,null,-1,10)));
        assertEquals(0,stats.getPrepareStatementCount());
    }

    @ParameterizedTest @ValueSource(strings={"unavailable","closed","unapproved"})
    void realProductCandidatesWithUntrustedOrIneligibleDependenciesAreNeverPublicResults(String mode) {
        create("Approved store product",CATEGORY_A,"10",ProductStatus.ACTIVE);
        create("Other store product",CATEGORY_A,"20",ProductStatus.ACTIVE,STORE_B,"SKU-B");
        var candidates=products.searchCandidates(query(null,null,null,null,null,0,10));
        // Trusted boundary fixture ONLY. There is deliberately no production public search provider.
        var validator=new CatalogBatchValidator(ids-> {
            if(mode.equals("unavailable"))throw new IllegalStateException("Fixture dependency unavailable");
            return candidates.candidates().stream().map(c-> {
                boolean bad=c.storeId().equals(STORE_B);
                var store=new ProductAccessPolicy.StoreAccess(c.storeId(),bad?SELLER_B:SELLER_A,"private-owner",
                        !(bad&&mode.equals("unapproved")),!(bad&&mode.equals("closed")),false);
                var p=new ProductListing(c.productId(),c.storeId(),c.categoryId(),c.inventorySku(),c.name(),
                        c.unitPrice(),c.currency(),ProductStatus.ACTIVE,null);
                return new LookupResult(c.productId(),LookupStatus.FOUND,new ResolvedProduct(p,store,c.currency(),true));
            }).toList();
        });
        var expected=candidates.candidates().stream().map(c->new ExpectedItem(c.productId(),c.storeId(),
                c.storeId().equals(STORE_A)?SELLER_A:SELLER_B,c.unitPrice(),c.currency())).toList();
        if(mode.equals("unavailable")) {
            assertEquals(CatalogBatchException.Code.DEPENDENCY_UNAVAILABLE,
                    assertThrows(CatalogBatchException.class,()->validator.validate(expected)).getCode());
        } else {
            var result=validator.validate(expected);
            assertFalse(result.valid());assertTrue(result.items().stream().allMatch(i->i.snapshot()==null));
        }
        assertEquals(2,candidates.candidateTotal(),"Never relabel this as total eligible Products or filter after paging");
        assertTrue(context.getBeansOfType(CatalogSnapshotSource.class).isEmpty());
        assertTrue(context.getBeansOfType(ProductAccessPolicy.class).isEmpty());
    }

    @Test void evenAWhollyEligibleCandidatePageDoesNotProveGlobalEligibleCount() {
        create("A1",CATEGORY_A,"10",ProductStatus.ACTIVE);
        create("A2",CATEGORY_A,"20",ProductStatus.ACTIVE);
        create("Z in a closed store",CATEGORY_A,"30",ProductStatus.ACTIVE,STORE_B,"closed-store");
        var page=products.searchCandidates(query(null,null,null,null,null,0,2));
        assertEquals(2,page.candidates().size());assertTrue(page.candidates().stream().allMatch(c->c.storeId().equals(STORE_A)));
        var checked=new CatalogBatchValidator(ids->page.candidates().stream().map(c-> {
            var p=new ProductListing(c.productId(),c.storeId(),c.categoryId(),c.inventorySku(),c.name(),
                    c.unitPrice(),c.currency(),ProductStatus.ACTIVE,null);
            var store=new ProductAccessPolicy.StoreAccess(STORE_A,SELLER_A,"private-owner",true,true,false);
            return new LookupResult(p.id(),LookupStatus.FOUND,new ResolvedProduct(p,store,p.currency(),true));
        }).toList()).validate(page.candidates().stream().map(c->new ExpectedItem(c.productId(),STORE_A,SELLER_A,
                c.unitPrice(),c.currency())).toList());
        assertTrue(checked.valid(),"Even verified facts for this page do not verify every remaining candidate");
        assertEquals(3,page.candidateTotal(),"Third candidate needs separate authority; no global public total is claimed");
        assertTrue(context.getBeansOfType(CatalogSnapshotSource.class).isEmpty());
    }

    @Test void defaultRuntimeCannotExposeCatalogEvenWithForgedAuthorityHeaders() throws Exception {
        for(String route:List.of("/api/catalog","/api/products","/internal/catalog/search","/internal/catalog/checkout/validate")) {
            var response=http.perform(MockMvcRequestBuilders.get(route).header("Authorization","Bearer forged")
                    .header("X-User-Id","owner").header("X-Roles","ADMIN,SELLER")).andReturn().getResponse();
            assertEquals(403,response.getStatus());
            assertTrue(response.getContentAsString().contains("PRODUCT_ROUTE_AUTH_NOT_CONFIGURED"));
        }
    }

    private CatalogSearchQuery query(String name,UUID category,String min,String max,Sort sort,int page,int size) {
        return new CatalogSearchQuery(name,category,min==null?null:new BigDecimal(min),max==null?null:new BigDecimal(max),sort,page,size);
    }
    private ProductListing create(String name,UUID category,String price,ProductStatus status) {
        return create(name,category,price,status,STORE_A,"SKU-"+(sequence+1));
    }
    private ProductListing create(String name,UUID category,String price,ProductStatus status,UUID store,String sku) {
        UUID id=UUID.fromString("00000000-0000-4000-8000-"+String.format("%012d",++sequence));
        return persistence.create(new ProductListing(id,store,category,sku,name,new BigDecimal(price),"VND",status,
                status==ProductStatus.REJECTED||status==ProductStatus.SUSPENDED?"private moderation reason":null)).listing();
    }
    private List<UUID> ids(CatalogCandidatePage page) {return page.candidates().stream().map(Candidate::productId).toList();}
    private TransactionTemplate requiresNew() {var tx=new TransactionTemplate(transactions);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);return tx;}

    @TestConfiguration static class InspectionFixture {
        @Bean ObservationInspector inspector() {return new ObservationInspector();}
        @Bean HibernatePropertiesCustomizer inspection(ObservationInspector inspector) {
            return properties->properties.put("hibernate.session_factory.statement_inspector",inspector);
        }
    }
    static class ObservationInspector implements StatementInspector {
        final AtomicReference<Runnable> beforeRows=new AtomicReference<>();
        final List<String> sql=new ArrayList<>();
        @Override public String inspect(String statement) {
            String lower=statement.toLowerCase(Locale.ROOT);sql.add(lower);
            if(lower.startsWith("select")&&lower.contains("from products")&&!lower.contains("count(")) {
                Runnable hook=beforeRows.getAndSet(null);if(hook!=null)hook.run();
            }
            return statement;
        }
    }
}
