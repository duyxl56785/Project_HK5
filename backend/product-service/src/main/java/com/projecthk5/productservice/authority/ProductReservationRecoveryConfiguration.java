package com.projecthk5.productservice.authority;

import com.projecthk5.security.IsolatedPairCredentials;
import com.projecthk5.security.fencing.*;
import com.projecthk5.security.fencing.DurableAuthorityReservations.Participant;
import java.net.URI;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.*;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="hk5.product.reservations.recovery.enabled",havingValue="true")
public class ProductReservationRecoveryConfiguration {
    @Bean
    ProductReservationRecoveryWorker productReservationRecoveryWorker(Environment env) {
        var source=new DriverManagerDataSource(env.getRequiredProperty("HK5_PRODUCT_RECOVERY_DB_URL"),
            env.getRequiredProperty("HK5_PRODUCT_RECOVERY_DB_USERNAME"),env.getRequiredProperty("HK5_PRODUCT_RECOVERY_DB_PASSWORD"));
        var properties=new Properties();properties.setProperty("options","-c lock_timeout=500ms -c statement_timeout=5000ms");source.setConnectionProperties(properties);
        return new ProductReservationRecoveryWorker(new ProductReservationRecoveryStore(source,Duration.ZERO),new DurableWriteLedger(source),
            client(env,"HK5_PRODUCT_AUTH_RESERVATION_URL","HK5_PRODUCT_AUTH_CURRENT_ACCOUNT_",Participant.AUTH),
            client(env,"HK5_PRODUCT_SELLER_RESERVATION_URL","HK5_PRODUCT_SELLER_STORE_FACTS_",Participant.SELLER),
            Duration.ofSeconds(env.getProperty("hk5.product.reservations.recovery.lease-seconds",Long.class,30L)),
            env.getProperty("hk5.product.reservations.recovery.max-attempts",Integer.class,32));
    }
    @Bean RecoverySchedule productRecoverySchedule(ProductReservationRecoveryWorker worker){return new RecoverySchedule(worker);}
    static final class RecoverySchedule {
        private final ProductReservationRecoveryWorker worker;
        RecoverySchedule(ProductReservationRecoveryWorker worker){this.worker=worker;}
        @Scheduled(fixedDelayString="${hk5.product.reservations.recovery.poll-ms:1000}")
        public void run() throws SQLException {worker.tick();}
    }
    private static ReservationRpcClient client(Environment env,String url,String prefix,Participant participant) {
        return new ReservationRpcClient(URI.create(env.getRequiredProperty(url)),()->{
            IsolatedPairCredentials.read(env::getProperty,prefix);return env.getRequiredProperty(prefix+"SECRET");
        },participant,null);
    }
}
