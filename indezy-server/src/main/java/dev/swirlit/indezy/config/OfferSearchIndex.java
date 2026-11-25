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
 *
 * <p>It also keeps the offers' rows narrow: their description and search text (about 3.5 KB together) go to the table's
 * out-of-line storage from {@value #TOAST_TARGET} bytes on, so the searches that score or count every open offer read a
 * table five times smaller (145 → 25 MB for 50,000 offers; Best match 327 → 288 ms alone). Rows written since are stored
 * so, the others as the collection rewrites them; a {@code VACUUM FULL job_offers} in a quiet hour moves them all at once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OfferSearchIndex implements ApplicationRunner {

    static final String INDEX = "idx_job_offers_search_text_trgm";
    /** The row size from which PostgreSQL moves an offer's long texts out of the row (2,032 bytes by default). */
    static final int TOAST_TARGET = 256;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!PostgresCatalog.isPostgres(jdbcTemplate) || !PostgresCatalog.tableExists(jdbcTemplate, "job_offers")) {
            return;
        }
        narrowRows();
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

    /** Sets the offers' row size target once (a short lock taken alongside reads and writes); a refusal only slows the search. */
    private void narrowRows() {
        String target = "toast_tuple_target=" + TOAST_TARGET;
        List<String> options = jdbcTemplate.queryForList("SELECT array_to_string(reloptions, ',') FROM pg_class WHERE relname = ?",
            String.class, "job_offers");
        if (options.stream().anyMatch(set -> set != null && List.of(set.split(",")).contains(target))) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE job_offers SET (" + target.replace("=", " = ") + ")");
            log.info("The offers' long texts now go out of their rows from {} bytes", TOAST_TARGET);
        } catch (DataAccessException e) {
            log.warn("The offers' row size target could not be set; the searches read wider rows: {}", e.getMostSpecificCause().getMessage());
        }
    }
}
