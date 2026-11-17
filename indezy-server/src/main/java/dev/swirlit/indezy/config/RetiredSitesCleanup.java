package dev.swirlit.indezy.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Takes Nexoris, a site Indezy no longer knows, out of the workspaces that were given it as a source before: the source
 * goes where no opportunity names it, and stays as the workspace's own where one does. Running it again changes nothing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
@RequiredArgsConstructor
@Slf4j
public class RetiredSitesCleanup implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int sources = jdbc.update("DELETE FROM sources WHERE LOWER(name) = 'nexoris' AND LOWER(link) LIKE '%nexoris.com%' " +
            "AND id NOT IN (SELECT source_id FROM projects WHERE source_id IS NOT NULL)");
        if (sources > 0) {
            log.info("Took Nexoris out of {} workspaces' sources", sources);
        }
    }
}
