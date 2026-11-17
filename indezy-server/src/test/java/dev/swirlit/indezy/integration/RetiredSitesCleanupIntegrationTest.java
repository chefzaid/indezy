package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.config.RetiredSitesCleanup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Nexoris leaves the workspaces: its source goes where no opportunity names it, and stays where one does. */
class RetiredSitesCleanupIntegrationTest extends TwoAccountsIntegrationTest {

    @Autowired
    private RetiredSitesCleanup cleanup;

    private long nexoris(Account account) {
        jdbcTemplate.update("INSERT INTO sources (name, type, link, is_listing, freelance_id, created_at, version) VALUES ('Nexoris', " +
            "'JOB_BOARD', 'https://www.nexoris.com', FALSE, ?, CURRENT_TIMESTAMP, 0)", account.freelanceId());
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM sources WHERE freelance_id = ?", Long.class, account.freelanceId());
    }

    @Test
    void takesNexorisOutUnlessAnOpportunityNamesIt() {
        long unused = nexoris(bob);
        long named = nexoris(alice);
        idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead", "dailyRate", 600, "status", "CONTACT", "clientId", aliceClientId,
            "sourceId", named, "freelanceId", alice.freelanceId()), Map.class));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM projects WHERE source_id = ?", Integer.class, named)).isOne();

        cleanup.run(null);
        cleanup.run(null);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sources WHERE id = ?", Integer.class, unused)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sources WHERE id = ?", Integer.class, named)).isOne();
    }
}
