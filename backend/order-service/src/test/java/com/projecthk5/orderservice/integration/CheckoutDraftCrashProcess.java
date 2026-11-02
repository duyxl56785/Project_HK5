package com.projecthk5.orderservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthk5.orderservice.domain.CheckoutDraft;
import com.projecthk5.orderservice.domain.InventoryOperationIntent;
import com.projecthk5.orderservice.ports.InventoryHttpAdapter;
import com.projecthk5.orderservice.ports.InventoryPort;
import com.projecthk5.orderservice.service.CheckoutDraftJournal;
import com.projecthk5.orderservice.service.InventoryOperationJournal;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Disposable child JVM only: halts without shutdown hooks after an actual transaction commit. */
public final class CheckoutDraftCrashProcess {
    public static void main(String[] args) throws Exception {
        try (var context=new AnnotationConfigApplicationContext(Config.class)) {
            ObjectMapper json=context.getBean(ObjectMapper.class);
            CheckoutDraft draft=json.readValue(System.in.readAllBytes(),CheckoutDraft.class);
            context.getBean(CheckoutDraftJournal.class).prepare(draft);
            if ("AFTER_RESERVE".equals(args[0])) {
                var inventory=new InventoryHttpAdapter(args[1],"fixture-pair",context.getBean(InventoryOperationJournal.class));
                inventory.reserve(draft.orderId(),draft.sellerOrders().stream().flatMap(s->s.items().stream())
                    .map(i->new InventoryPort.Reservation(i.productId(),i.quantity(),i.sku())).toList());
            } else if ("AFTER_T1".equals(args[0])) {
                context.getBean(InventoryOperationJournal.class).prepare(InventoryOperationIntent.prepare(draft.orderId(),
                    InventoryHttpAdapter.reserveId(draft.orderId()),InventoryOperationIntent.Type.RESERVE,
                    draft.sellerOrders().stream().flatMap(s->s.items().stream())
                        .map(i->new InventoryOperationIntent.Line(i.sku(),i.quantity())).toList()));
            } else if (!"AFTER_DRAFT".equals(args[0])) throw new IllegalArgumentException("Invalid crash point");
            Runtime.getRuntime().halt(86);
        }
    }

    @TestConfiguration(proxyBeanMethods=false)
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource(System.getenv("TV3_CRASH_PG_URL"),
            System.getenv("TV3_CRASH_PG_USER"),System.getenv("TV3_CRASH_PG_PASSWORD"));}
        @Bean JdbcTemplate db(DataSource source){return new JdbcTemplate(source);}
        @Bean PlatformTransactionManager transactions(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean ObjectMapper json(){return new ObjectMapper().findAndRegisterModules();}
        @Bean CheckoutDraftJournal drafts(JdbcTemplate db,ObjectMapper json){return new CheckoutDraftJournal(db,json);}
        @Bean InventoryOperationJournal inventoryJournal(JdbcTemplate db,ObjectMapper json){return new InventoryOperationJournal(db,json);}
    }
}
