package com.projecthk5.security.fencing;

import java.sql.*;
import javax.sql.DataSource;

final class FenceSql {
    private FenceSql() { }
    interface Work<T> { T run(Connection connection) throws SQLException; }
    static <T> T transaction(DataSource source,Work<T> work) throws SQLException {
        try(var connection=source.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {T value=work.run(connection);connection.commit();return value;}
            catch(SQLException | RuntimeException | Error failure) {
                try {connection.rollback();}catch(SQLException rollback){failure.addSuppressed(rollback);}
                throw failure;
            }
        }
    }
    static void require(boolean valid) {
        if(!valid)throw new IllegalStateException("FENCE_CONFLICT_OR_UNAVAILABLE");
    }
}
