package dev.swirlit.indezy.config;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;

/** What the schema runners ask the database before changing what Hibernate cannot declare. */
final class PostgresCatalog {

    private PostgresCatalog() {
    }

    /** Whether the database is PostgreSQL (the tests run on H2). */
    static boolean isPostgres(JdbcTemplate jdbcTemplate) {
        String product = jdbcTemplate.execute((Connection connection) -> connection.getMetaData().getDatabaseProductName());
        return "PostgreSQL".equalsIgnoreCase(product);
    }

    /** Whether Hibernate already created the table. */
    static boolean tableExists(JdbcTemplate jdbcTemplate, String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, table));
    }
}
