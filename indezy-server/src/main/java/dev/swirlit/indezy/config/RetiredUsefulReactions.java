package dev.swirlit.indezy.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Locale;

/**
 * Empties {@code network_usefuls}: the **Useful** reaction on posts is gone, Like and its reactions saying as much, and
 * who found what useful is not kept. The table stays, empty, for the version still running while a new one starts (it
 * still reads it); a schema created since has no such table, and nothing to empty.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
@Slf4j
public class RetiredUsefulReactions implements ApplicationRunner {

    static final String TABLE = "network_usefuls";

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists()) {
            return;
        }
        int emptied = jdbc.update("DELETE FROM " + TABLE);
        if (emptied > 0) {
            log.info("Removed {} Useful reactions, the reaction being gone", emptied);
        }
    }

    private boolean tableExists() {
        return Boolean.TRUE.equals(jdbc.execute((Connection connection) -> {
            DatabaseMetaData metaData = connection.getMetaData();
            // PostgreSQL keeps names in lower case, H2 in upper case.
            for (String name : new String[] {TABLE, TABLE.toUpperCase(Locale.ROOT)}) {
                try (ResultSet tables = metaData.getTables(null, null, name, new String[] {"TABLE"})) {
                    if (tables.next()) {
                        return true;
                    }
                }
            }
            return false;
        }));
    }
}
