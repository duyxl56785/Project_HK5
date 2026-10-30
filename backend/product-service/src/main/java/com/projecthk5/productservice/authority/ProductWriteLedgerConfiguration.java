package com.projecthk5.productservice.authority;

import com.projecthk5.security.fencing.DurableWriteLedger;
import javax.sql.DataSource;
import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnExpression("${hk5.product.receipts.enabled:false} or ${hk5.product.reservations.client.enabled:false}")
public class ProductWriteLedgerConfiguration {
    @Bean
    @ConditionalOnProperty(name="hk5.product.reservations.client.enabled",havingValue="true")
    ProductReservationRecoveryStore productReservationRecoveryStore(DataSource source,Environment env) {
        return new ProductReservationRecoveryStore(source,Duration.ofSeconds(env.getProperty("hk5.product.reservations.recovery.grace-seconds",Long.class,60L)));
    }
    @Bean
    @ConditionalOnMissingBean(DurableWriteLedger.class)
    DurableWriteLedger productDurableWriteLedger(DataSource source) {
        return new DurableWriteLedger(source);
    }
}
