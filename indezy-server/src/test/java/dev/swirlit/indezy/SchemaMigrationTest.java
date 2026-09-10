package dev.swirlit.indezy;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchemaMigrationTest {

    private final DataSource database = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final Statement statement = mock(Statement.class);
    @SuppressWarnings("unchecked")
    private final Consumer<Connection> migration = mock(Consumer.class);

    private void connected() throws SQLException {
        when(database.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
    }

    @Test
    void schemaChangesCannotStartWhenLockAcquisitionFails() throws SQLException {
        connected();
        when(statement.execute(anyString())).thenThrow(new SQLException("lock timeout"));

        assertThrows(SQLException.class, () -> SchemaMigration.run(database, migration));

        verify(migration, never()).accept(connection);
        verify(connection).close();
    }

    @Test
    void startupFailureReleasesTheLockAndPropagates() throws SQLException {
        connected();
        doThrow(new IllegalStateException("DDL failed")).when(migration).accept(connection);

        assertThrows(IllegalStateException.class, () -> SchemaMigration.run(database, migration));

        verify(connection).close();
    }

    @Test
    void successfulMigrationRunsInsideTheLockLifetime() throws SQLException {
        connected();
        Consumer<Connection> guarded = lockedConnection -> {
            try {
                org.junit.jupiter.api.Assertions.assertSame(connection, lockedConnection);
                verify(statement).execute("SELECT pg_advisory_lock(1768842341, 1)");
                verify(connection, never()).close();
            } catch (SQLException failure) {
                throw new AssertionError(failure);
            }
        };

        SchemaMigration.run(database, guarded);

        verify(connection).close();
    }
}
