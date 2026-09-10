package dev.swirlit.indezy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;

/** Serializes schema hooks before Spring initializes Hibernate. */
final class SchemaMigration {

    private SchemaMigration() {
    }

    static void run(DataSource database, Consumer<Connection> migration) throws SQLException {
        // Session-scoped PostgreSQL locks are released on connection close,
        // including failed startup or process loss. This connection stays open
        // until the migration's Spring context has closed. DDL must use this
        // same connection so a lost database session also stops schema changes.
        try (var connection = database.getConnection();
             var statement = connection.createStatement()) {
            statement.setQueryTimeout(300);
            statement.execute("SELECT pg_advisory_lock(1768842341, 1)");
            migration.accept(connection);
        }
    }
}
