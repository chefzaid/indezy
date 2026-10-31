package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alice won a mission at a bank through an ESN, with a 12-month non-solicitation clause: the bank's direct offer is
 * flagged until twelve months after the mission, over HTTP, and Bob sees none of it.
 */
class NonSolicitationIntegrationTest extends TwoAccountsIntegrationTest {

    private long client(String name, boolean isFinal) {
        return ((Number) call(alice, HttpMethod.POST, "/clients", Map.of("companyName", name, "city", "Paris", "isFinal", isFinal,
            "freelanceId", alice.freelanceId()), Map.class).getBody().get("id")).longValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void flagsTheEndClientsDirectOfferWhileTheClauseRuns() {
        long esn = client("ESN Conseil", false);
        LocalDate start = LocalDate.now().minusMonths(2);
        long mission = ((Number) call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650,
            "status", "WON", "clientId", aliceClientId, "middlemanId", esn, "startDate", start.toString(), "durationInMonths", 6,
            "nonSolicitationMonths", 12), Map.class).getBody().get("id")).longValue();
        long direct = ((Number) call(alice, HttpMethod.POST, "/projects", Map.of("role", "Architecte", "dailyRate", 750,
            "clientId", aliceClientId), Map.class).getBody().get("id")).longValue();

        List<Map<String, Object>> covered = call(alice, HttpMethod.GET, "/projects/non-solicitation/" + alice.freelanceId(), null,
            List.class).getBody();
        assertThat(covered).singleElement().satisfies(entry -> assertThat(entry)
            .containsEntry("projectId", (int) direct).containsEntry("missionId", (int) mission)
            .containsEntry("middlemanName", "ESN Conseil").containsEntry("months", 12)
            .containsEntry("endsOn", start.plusMonths(6).minusDays(1).plusMonths(12).toString()));

        assertThat(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Too long", "dailyRate", 600, "clientId", aliceClientId,
            "nonSolicitationMonths", 37), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(bob, HttpMethod.GET, "/projects/non-solicitation/" + alice.freelanceId(), null, String.class)
            .getStatusCode().is4xxClientError()).isTrue();
        assertThat(call(bob, HttpMethod.GET, "/projects/non-solicitation/" + bob.freelanceId(), null, List.class).getBody()).isEmpty();
    }
}
