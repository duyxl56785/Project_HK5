package com.projecthk5.sellerservice.outbox;
import java.net.URI;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="hk5.seller.role-sync.dispatch.enabled",havingValue="true")
@EnableScheduling
public class RoleSyncDispatchConfiguration {
    @Bean DeliveryStore roleDeliveryStore(DataSource source){return new DeliveryStore(source);}
    @Bean RoleSyncClient roleDeliveryClient(Environment env){return new RoleSyncClient(URI.create(env.getRequiredProperty("HK5_AUTH_ROLE_SYNC_URL")),()->env.getProperty("HK5_SELLER_AUTH_ROLE_SYNC_SECRET"));}
    @Bean SellerRoleDispatcher sellerRoleDispatcher(DeliveryStore store,RoleSyncClient client){return new SellerRoleDispatcher(store,client);}
    @Bean Worker sellerRoleWorker(SellerRoleDispatcher dispatcher){return new Worker(dispatcher);}
    public static final class Worker {
        private final SellerRoleDispatcher dispatcher;
        private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(Worker.class);
        public Worker(SellerRoleDispatcher dispatcher){this.dispatcher=dispatcher;}
        @Scheduled(fixedDelayString="${hk5.seller.role-sync.poll-delay-ms:1000}")
        public void poll(){try{dispatcher.runOnce();}catch(RuntimeException unavailable){log.warn("Seller role delivery temporarily unavailable");}}
    }
}
