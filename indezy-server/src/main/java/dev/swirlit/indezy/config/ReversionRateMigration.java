package dev.swirlit.indezy.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Turns the reversion rates saved as the share taken (social charges and income tax, in {@code reversion_rate}) into
 * the share kept, the taux de réversion, in {@code reversion_share}: 100 less the old value. The old value is emptied
 * as it moves, so running again changes nothing. The old column stays, empty, for the version still running while a
 * new one starts; a schema created since has no such column, and nothing to move.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
@RequiredArgsConstructor
@Slf4j
public class ReversionRateMigration implements ApplicationRunner {

    static final String TABLE = "freelances";
    static final String LEGACY_COLUMN = "reversion_rate";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!legacyColumnExists()) {
            return;
        }
        Integer moved = transactionTemplate.execute(status -> jdbcTemplate.update(
            "UPDATE " + TABLE + " SET reversion_share = 100 - " + LEGACY_COLUMN + ", " + LEGACY_COLUMN + " = NULL " +
                "WHERE " + LEGACY_COLUMN + " IS NOT NULL"));
        if (moved != null && moved > 0) {
            log.info("{} reversion rates now say the share kept rather than the share taken", moved);
        }
    }

    private boolean legacyColumnExists() {
        return Boolean.TRUE.equals(jdbcTemplate.execute((Connection connection) -> {
            DatabaseMetaData metaData = connection.getMetaData();
            // PostgreSQL keeps names in lower case, H2 in upper case.
            return hasColumn(metaData, TABLE, LEGACY_COLUMN) || hasColumn(metaData, TABLE.toUpperCase(), LEGACY_COLUMN.toUpperCase());
        }));
    }

    private static boolean hasColumn(DatabaseMetaData metaData, String table, String column) throws SQLException {
        try (ResultSet columns = metaData.getColumns(null, null, table, column)) {
            return columns.next();
        }
    }
}
