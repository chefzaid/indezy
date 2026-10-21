package dev.swirlit.indezy.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gives PostgreSQL a trigram index on the offers' search text, so the match score finds the offers naming a profile's
 * skill ({@code like '%skill%'}) through the index instead of reading every offer's text (see
 * docs/architecture.md#offer-search-at-scale). Hibernate cannot declare such an index, hence this runner: it does
 * nothing once the index exists, builds it without blocking the collection's writes, and builds again one an
 * interrupted build left invalid. Without the {@code pg_trgm} extension (a role that may not create it) or on another
 * database it changes nothing, and the search still works.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OfferSearchIndex implements ApplicationRunner {

    static final String INDEX = "idx_job_offers_search_text_trgm";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!PostgresCatalog.isPostgres(jdbcTemplate) || !PostgresCatalog.tableExists(jdbcTemplate, "job_offers")) {
            return;
        }
        List<Boolean> valid = jdbcTemplate.queryForList(
            "SELECT i.indisvalid FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid WHERE c.relname = ?", Boolean.class, INDEX);
        if (valid.contains(Boolean.TRUE)) {
            return;
        }
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            if (!valid.isEmpty()) {
                jdbcTemplate.execute("DROP INDEX CONCURRENTLY IF EXISTS " + INDEX);
            }
            long start = System.nanoTime();
            // Concurrently: the collection keeps writing offers while the index is built.
            jdbcTemplate.execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS " + INDEX + " ON job_offers USING gin (search_text gin_trgm_ops)");
            log.info("Built the offers' trigram search index in {} ms", (System.nanoTime() - start) / 1_000_000);
        } catch (DataAccessException e) {
            log.warn("The offers' trigram search index could not be built; the match score reads every offer's text: {}",
                e.getMostSpecificCause().getMessage());
        }
    }
}
