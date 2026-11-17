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
 * Splits the refused opportunities into the two statuses, once their side is known ({@link RefusedByBackfill} runs
 * first): those the freelance turned down are withdrawn; the others stay abandoned, by the recruiter, those whose side
 * was never said included. Running it again changes nothing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
@RequiredArgsConstructor
@Slf4j
public class RefusalStatusMigration implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int withdrawn = jdbc.update("UPDATE projects SET status = 'WITHDRAWN' WHERE status = 'LOST' AND refused_by = 'FREELANCE'");
        int abandoned = jdbc.update("UPDATE projects SET refused_by = 'RECRUITER' WHERE status = 'LOST' AND refused_by IS NULL");
        if (withdrawn + abandoned > 0) {
            log.info("Refused opportunities split: {} withdrawn by the freelance, {} more abandoned by the recruiter", withdrawn, abandoned);
        }
    }
}
