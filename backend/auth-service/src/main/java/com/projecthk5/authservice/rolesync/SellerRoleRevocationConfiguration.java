package com.projecthk5.authservice.rolesync;

import com.projecthk5.security.fencing.RevocationBarrier;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"hk5.auth.role-sync.enabled","hk5.auth.role-sync.revocation-recovery.enabled"},havingValue="true")
public class SellerRoleRevocationConfiguration {
    @Bean
    SellerRoleRevocationRecovery sellerRoleRevocationRecovery(DataSource source,SellerRoleSyncService service) {
        return new SellerRoleRevocationRecovery(new RevocationBarrier(source),service);
    }
    @Configuration(proxyBeanMethods=false)
    @EnableScheduling
    @ConditionalOnProperty(name={"hk5.auth.role-sync.enabled","hk5.auth.role-sync.revocation-recovery.enabled","hk5.auth.role-sync.revocation-worker.enabled"},havingValue="true")
    public static class WorkerConfiguration {
        @Bean Worker sellerRoleRevocationWorker(SellerRoleRevocationRecovery recovery) {return new Worker(recovery);}
    }
    public static final class Worker {
        private final SellerRoleRevocationRecovery recovery;
        public Worker(SellerRoleRevocationRecovery recovery) {this.recovery=recovery;}
        @Scheduled(fixedDelayString="${hk5.auth.role-sync.revocation-worker.delay-ms:1000}")
        public void tick() {try {recovery.tick();}catch(Exception unavailable) { /* durable queue retries next tick */ }}
    }
}
