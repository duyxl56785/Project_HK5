package com.projecthk5.security;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Lets Auth stop a write whose authority was revoked while it ran. Each commit of a reserved write
 * holds a shared advisory lock and checks for a tombstone; close takes that lock exclusively and leaves
 * the tombstone, so when close returns the write has either committed or can never commit.
 * Only the request thread is covered, so a write must not move to another thread.
 * shortcut: tombstones are never purged, purge them if revocations ever make the table large.
 */
public final class WriteFence implements TransactionExecutionListener {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    private final JdbcTemplate jdbc;

    public WriteFence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static void enter(UUID operation) {
        CURRENT.set(operation);
    }

    public static void exit() {
        CURRENT.remove();
    }

    @Override
    public void beforeCommit(TransactionExecution transaction) {
        UUID operation = CURRENT.get();
        if (operation == null || transaction.isReadOnly()) return;
        Integer isolation = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        // An older snapshot could not see a tombstone written after the transaction began.
        if (isolation != null && isolation != TransactionDefinition.ISOLATION_READ_COMMITTED)
            throw new IllegalStateException("A reserved write must run at READ COMMITTED");
        jdbc.query("select pg_advisory_xact_lock_shared(hashtextextended(?::text, 0))", rs -> {}, operation);
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from write_fence_abort where operation_id = ?)", Boolean.class, operation)))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_ACCESS_INVALID", "Access token no longer matches the account");
    }

    /** Idempotent; blocks while a commit of this operation is past its check. */
    public void close(UUID operation) {
        jdbc.update("""
                with locked as (select pg_advisory_xact_lock(hashtextextended(?::text, 0)))
                insert into write_fence_abort (operation_id) select ? from locked on conflict do nothing
                """, operation, operation);
    }
}
