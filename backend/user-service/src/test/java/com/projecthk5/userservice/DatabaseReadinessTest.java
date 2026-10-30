package com.projecthk5.userservice;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("migration")
@EnabledIfEnvironmentVariable(named = "TV1_DATABASE_TESTS", matches = "true")
class DatabaseReadinessTest {
    @Autowired
    private DataSource dataSource;
    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Test
    void migrationProfileMigratesWithSeparateRoleAndRuntimeJpaValidates() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT current_user, current_database()")) {
            assertTrue(result.next());
            assertEquals("user_app", result.getString(1));
            assertEquals("user_db", result.getString(2));
        }
        var flyway = Flyway.configure()
                .dataSource(jdbcUrl, "user_migrator", System.getenv("USER_MIGRATION_PASSWORD"))
                .cleanDisabled(true).locations("classpath:db/migration").load();
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.info().pending().length);
        assertEquals("1", flyway.info().current().getVersion().getVersion());
    }
}
