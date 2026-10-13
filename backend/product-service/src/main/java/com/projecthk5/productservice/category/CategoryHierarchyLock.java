package com.projecthk5.productservice.category;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class CategoryHierarchyLock {
    private final JdbcTemplate jdbcTemplate;

    public CategoryHierarchyLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquire() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Category hierarchy lock requires a transaction");
        }
        // All Category writers share this key; PostgreSQL releases it at commit/rollback.
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(2142, 1)");
    }
}
