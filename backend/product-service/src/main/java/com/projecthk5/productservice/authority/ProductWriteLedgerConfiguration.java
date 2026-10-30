package com.projecthk5.productservice.authority;

import com.projecthk5.security.fencing.DurableWriteLedger;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnExpression("${hk5.product.receipts.enabled:false} or ${hk5.product.reservations.client.enabled:false}")
public class ProductWriteLedgerConfiguration {
    @Bean
    @ConditionalOnMissingBean(DurableWriteLedger.class)
    DurableWriteLedger productDurableWriteLedger(DataSource source) {
        return new DurableWriteLedger(source);
    }
}
