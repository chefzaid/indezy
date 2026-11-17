package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.config.RefusalStatusMigration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The refused opportunities split in two: withdrawn when the freelance refused, abandoned otherwise, side unsaid included. */
class RefusalStatusMigrationIntegrationTest extends TwoAccountsIntegrationTest {

    @Autowired
    private RefusalStatusMigration migration;

    private long refused(String side) {
        long id = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead", "dailyRate", 600, "status", "CONTACT",
            "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class));
        jdbcTemplate.update("UPDATE projects SET status = 'LOST', refused_by = ? WHERE id = ?", side, id);
        return id;
    }

    private Map<String, Object> row(long id) {
        return jdbcTemplate.queryForMap("SELECT status, refused_by FROM projects WHERE id = ?", id);
    }

    @Test
    void splitsTheRefusedOpportunitiesByWhoRefused() {
        long freelance = refused("FREELANCE");
        long recruiter = refused("RECRUITER");
        long unsaid = refused(null);

        migration.run(null);
        migration.run(null);

        assertThat(row(freelance)).containsEntry("STATUS", "WITHDRAWN").containsEntry("REFUSED_BY", "FREELANCE");
        assertThat(row(recruiter)).containsEntry("STATUS", "LOST").containsEntry("REFUSED_BY", "RECRUITER");
        assertThat(row(unsaid)).containsEntry("STATUS", "LOST").containsEntry("REFUSED_BY", "RECRUITER");
    }
}
