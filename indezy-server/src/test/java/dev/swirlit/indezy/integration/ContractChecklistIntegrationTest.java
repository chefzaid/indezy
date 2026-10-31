package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice ticks her mission's contract checklist over HTTP; a full edit of the mission keeps it, and Bob cannot touch it. */
class ContractChecklistIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void keepsTheCheckedPointsAndTheirNotes() {
        long mission = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650, "status", "WON",
            "clientId", aliceClientId), Map.class));
        String checks = "/projects/" + mission + "/contract-checks";

        Map<String, Object> saved = call(alice, HttpMethod.PUT, checks, Map.of("checks", List.of(
            Map.of("point", "NOTICE_PERIOD", "checked", true, "note", "  15 jours  "),
            Map.of("point", "PAYMENT_TERMS", "checked", true, "note", "45 jours fin de mois"),
            Map.of("point", "RATE_REVISION", "checked", false))), Map.class).getBody();
        assertThat((List<Map<String, Object>>) saved.get("contractChecks")).extracting(check -> check.get("point"))
            .containsExactly("PAYMENT_TERMS", "NOTICE_PERIOD", "RATE_REVISION");

        Map<String, Object> project = call(alice, HttpMethod.GET, "/projects/" + mission, null, Map.class).getBody();
        project.remove("contractChecks");
        project.put("dailyRate", 700);
        call(alice, HttpMethod.PUT, "/projects/" + mission, project, Map.class);
        List<Map<String, Object>> kept = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/projects/" + mission, null, Map.class)
            .getBody().get("contractChecks");
        assertThat(kept).hasSize(3);
        assertThat(kept.get(1)).containsEntry("note", "15 jours").containsEntry("checked", true);

        assertThat(call(alice, HttpMethod.PUT, checks, Map.of("checks", List.of(Map.of("point", "NOTICE_PERIOD", "checked", true),
            Map.of("point", "NOTICE_PERIOD", "checked", false))), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.PUT, checks, Map.of("checks", List.of(Map.of("point", "NOTICE_PERIOD", "checked", true,
            "note", "x".repeat(501)))), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(bob, HttpMethod.PUT, checks, Map.of("checks", List.of()), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
