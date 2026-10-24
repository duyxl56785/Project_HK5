package com.projecthk5.tv5authqa;

import com.projecthk5.authservice.AuthServiceApplication;
import com.projecthk5.authservice.persistence.Account;
import com.projecthk5.authservice.persistence.AccountRepository;
import com.projecthk5.authservice.security.CurrentAccountAuthority;
import com.projecthk5.authservice.security.Hk5JwtCodec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

/** Test-only Spring wiring and disposable DB; does not enable Auth routes or select production policy. */
@Tag("postgres-integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CurrentAccountAuthorityPostgresRegressionTest {
    private static final UUID ID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final String ACTIVE = "QA_EXPLICIT_ACTIVE";
    private final String appPassword = UUID.randomUUID().toString();
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("auth_db").withUsername("auth_migrator")
            .withPassword(UUID.randomUUID().toString());
    private ConfigurableApplicationContext context;
    private CurrentAccountAuthority authority;
    private Hk5JwtCodec jwt;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestBeans {
        @Bean CurrentAccountAuthority tv5CurrentAuthority(AccountRepository accounts) {
            return new CurrentAccountAuthority(accounts, ACTIVE);
        }
    }

    @BeforeAll void startIsolatedFixture() throws Exception {
        postgres.start();
        sql("CREATE ROLE auth_app LOGIN PASSWORD '" + appPassword + "'");
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").cleanDisabled(true).load().migrate();
        sql("GRANT USAGE ON SCHEMA public TO auth_app");
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("tv5-isolated-auth-qa", Map.of(
                "spring.datasource.url", postgres.getJdbcUrl(),
                "spring.datasource.username", "auth_app",
                "spring.datasource.password", appPassword,
                "spring.flyway.enabled", "false",
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.jpa.open-in-view", "false",
                "spring.main.banner-mode", "off")));
        context = new SpringApplicationBuilder(AuthServiceApplication.class, TestBeans.class)
                .web(WebApplicationType.NONE).environment(environment).run();
        authority = context.getBean(CurrentAccountAuthority.class);
        byte[] fakeKey = new byte[32];
        Arrays.fill(fakeKey, (byte) 41);
        jwt = new Hk5JwtCodec("https://tv5-qa.example.invalid", "tv5-qa-only", "qa",
                Map.of("qa", new SecretKeySpec(fakeKey, "HmacSHA256")), Clock.systemUTC());
        Arrays.fill(fakeKey, (byte) 0);
    }

    @AfterAll void closeOwnResources() {
        if (context != null) context.close();
        postgres.stop();
    }

    @BeforeEach void resetOwnFixture() throws Exception {
        sql("GRANT SELECT ON accounts, account_role_grants TO auth_app");
        sql("DELETE FROM account_role_grants");
        sql("DELETE FROM accounts");
        sql("INSERT INTO accounts(id,email,password_hash,status,created_at) VALUES ('" + ID
                + "','fixture@example.invalid','qa-only-not-a-password','" + ACTIVE + "',CURRENT_TIMESTAMP)");
        sql("INSERT INTO account_role_grants(account_id,role) VALUES ('" + ID + "','CUSTOMER')");
    }

    @Test void transactionalProxyLoadsLazyRolesAndCurrentRevocationOverridesSignedAdmin() throws Exception {
        assertTrue(AopUtils.isAopProxy(authority), "The actual authority must execute under a Spring transaction proxy");
        grantAdmin();
        String token = jwt.issue(authority.requireCurrentForIssue(ID));
        var principal = jwt.verify(token);
        assertEquals(Set.of(Account.Role.CUSTOMER, Account.Role.ADMIN), principal.tokenRoles());
        assertEquals(ID, authority.requireCurrentForRequest(principal, Account.Role.ADMIN).accountId());
        sql("DELETE FROM account_role_grants WHERE account_id='" + ID + "' AND role='ADMIN'");
        assertStatus(403, () -> authority.requireCurrentForRequest(principal, Account.Role.ADMIN));
    }

    @Test void currentGrantCannotElevateOldTokenButFreshlyIssuedTokenCanUseIt() throws Exception {
        var oldPrincipal = jwt.verify(jwt.issue(authority.requireCurrentForIssue(ID)));
        grantAdmin();
        assertStatus(403, () -> authority.requireCurrentForRequest(oldPrincipal, Account.Role.ADMIN));
        var fresh = jwt.verify(jwt.issue(authority.requireCurrentForIssue(ID)));
        assertEquals(ID, authority.requireCurrentForRequest(fresh, Account.Role.ADMIN).accountId());
    }

    @Test void inactiveAndMissingCurrentAccountsReturn401() throws Exception {
        var principal = jwt.verify(jwt.issue(authority.requireCurrentForIssue(ID)));
        sql("UPDATE accounts SET status='QA_INACTIVE' WHERE id='" + ID + "'");
        assertStatus(401, () -> authority.requireCurrentForRequest(principal, Account.Role.CUSTOMER));
        assertStatus(401, () -> authority.requireCurrentForIssue(ID));
        assertStatus(401, () -> authority.requireCurrentForIssue(UUID.randomUUID()));
    }

    @Test void unavailableAccountQueryIs503RatherThanAnInvalidAccount() throws Exception {
        sql("REVOKE SELECT ON accounts FROM auth_app");
        try {
            assertStatus(503, () -> authority.requireCurrentForIssue(ID));
        } finally {
            sql("GRANT SELECT ON accounts TO auth_app");
        }
    }

    @Test void unavailableLazyRoleQueryIsAlso503RatherThanLeakingPersistenceFailure() throws Exception {
        sql("REVOKE SELECT ON account_role_grants FROM auth_app");
        try {
            assertStatus(503, () -> authority.requireCurrentForIssue(ID));
        } finally {
            sql("GRANT SELECT ON account_role_grants TO auth_app");
        }
    }

    private void assertStatus(int expected, org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(CurrentAccountAuthority.DecisionFailure.class, action);
        assertEquals(expected, failure.httpStatus());
        if (expected == 503) assertEquals("AUTHORIZATION_DEPENDENCY_UNAVAILABLE", failure.code());
    }

    private void grantAdmin() throws Exception {
        sql("INSERT INTO account_role_grants(account_id,role) VALUES ('" + ID + "','ADMIN')");
    }

    private void sql(String statement) throws Exception {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
             PreparedStatement query = connection.prepareStatement(statement)) {
            query.execute();
        }
    }
}
