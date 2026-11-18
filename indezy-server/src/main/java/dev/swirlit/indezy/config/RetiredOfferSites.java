package dev.swirlit.indezy.config;

import dev.swirlit.indezy.model.enums.OfferSite;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Removes what Indezy kept of a job board it no longer reads.
 *
 * <p>Every site column stores the {@link OfferSite} name, which Hibernate cannot load once the board has left the enum,
 * so a retired board's rows would break the offer feed, the site health page and the collection. Before the
 * application takes traffic, this runner deletes the rows that name another value in every column mapped to
 * {@link OfferSite}: the board's offers with the rows that refer to them, its runs, skips, copies, visits and
 * workspace exclusions. It changes nothing once no row names a retired board, and nothing on another database.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 6)
@Slf4j
public class RetiredOfferSites implements ApplicationRunner {

    static final String OFFERS = "job_offers";

    /** The single-column foreign keys to the offers that do not cascade, as table and column. */
    static final String REFERRERS = "SELECT c.conrelid::regclass::text AS referrer, a.attname AS column_name FROM pg_constraint c " +
        "JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1] " +
        "WHERE c.contype = 'f' AND c.confrelid = 'job_offers'::regclass AND c.confdeltype <> 'c' AND cardinality(c.conkey) = 1";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Supplier<List<String>> siteColumns;

    @Autowired
    public RetiredOfferSites(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate, EntityManagerFactory entityManagerFactory) {
        this(jdbcTemplate, transactionTemplate, () -> siteColumns(EnumCheckConstraints.enumColumns(
            entityManagerFactory.unwrap(SessionFactoryImplementor.class).getMappingMetamodel())));
    }

    RetiredOfferSites(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate, Supplier<List<String>> siteColumns) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.siteColumns = siteColumns;
    }

    /** The {@code table.column} keys of the columns that store an {@link OfferSite}, in name order. */
    static List<String> siteColumns(Map<String, List<String>> enumColumns) {
        List<String> sites = Arrays.stream(OfferSite.values()).map(Enum::name).toList();
        return enumColumns.entrySet().stream()
            .filter(column -> column.getValue().equals(sites))
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    }

    /** The condition a row of a retired board meets. */
    static String retired(String column) {
        return "\"" + column + "\" NOT IN (" + Arrays.stream(OfferSite.values())
            .map(site -> "'" + site.name() + "'")
            .collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!PostgresCatalog.isPostgres(jdbcTemplate)) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            int removed = 0;
            for (String key : siteColumns.get()) {
                String table = key.substring(0, key.indexOf('.'));
                String column = key.substring(key.indexOf('.') + 1);
                if (!PostgresCatalog.tableExists(jdbcTemplate, table)) {
                    continue;
                }
                if (OFFERS.equals(table)) {
                    for (Map<String, Object> referrer : jdbcTemplate.queryForList(REFERRERS)) {
                        jdbcTemplate.update("DELETE FROM " + referrer.get("referrer") + " WHERE \"" + referrer.get("column_name") +
                            "\" IN (SELECT id FROM \"" + OFFERS + "\" WHERE " + retired(column) + ")");
                    }
                }
                removed += jdbcTemplate.update("DELETE FROM \"" + table + "\" WHERE " + retired(column));
            }
            if (removed > 0) {
                log.info("Removed {} rows of job boards Indezy no longer reads", removed);
            }
        });
    }
}
