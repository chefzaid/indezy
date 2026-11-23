package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice reads how an ESN dealt with her, from her own opportunities through it; Bob cannot read it. */
class CompanyScorecardIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void sumsUpTheOpportunitiesThroughAnEsn() {
        long esn = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Score ESN", "city", "Lyon", "isFinal", false,
            "freelanceId", alice.freelanceId()), Map.class));
        long endClient = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Score Banque", "city", "Lyon", "isFinal", true,
            "freelanceId", alice.freelanceId()), Map.class));
        assertThat(call(alice, HttpMethod.GET, "/clients/" + esn + "/scorecard", null, Map.class).getBody())
            .containsEntry("opportunities", 0).containsEntry("offerRate", null).containsEntry("processDays", null);

        long won = opportunity(esn, endClient, "WON", 600, 750);
        long lost = opportunity(esn, endClient, "LOST", 500, 700);
        opportunity(esn, endClient, "CONTACT", 550, null);
        opportunity(esn, endClient, "OFFER", 620, null);
        step(won, 10);
        step(won, 4);
        step(lost, 20);

        Map<String, Object> scorecard = call(alice, HttpMethod.GET, "/clients/" + esn + "/scorecard", null, Map.class).getBody();
        assertThat(scorecard).containsEntry("opportunities", 4).containsEntry("open", 2).containsEntry("won", 1)
            .containsEntry("offerRate", 67).containsEntry("processDays", 15).containsEntry("processes", 2)
            .containsEntry("marginMin", 20).containsEntry("marginMax", 29).containsEntry("margins", 2).containsEntry("answerDays", null);
        // The end client has the same opportunities, but no margin of its own.
        assertThat(call(alice, HttpMethod.GET, "/clients/" + endClient + "/scorecard", null, Map.class).getBody())
            .containsEntry("opportunities", 4).containsEntry("margins", 0).containsEntry("marginMin", null);
        assertThat(call(bob, HttpMethod.GET, "/clients/" + esn + "/scorecard", null, String.class).getStatusCode())
            .isNotEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings("unchecked")
    private long opportunity(long esn, long endClient, String status, int rate, Integer clientRate) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("role", "Lead Java", "dailyRate", rate, "status", status,
            "clientId", endClient, "middlemanId", esn));
        if (clientRate != null) {
            body.put("clientDailyRate", clientRate);
        }
        return idOf(call(alice, HttpMethod.POST, "/projects", body, Map.class));
    }

    private void step(long project, int inDays) {
        call(alice, HttpMethod.POST, "/interview-steps", Map.of("title", "Entretien", "status", "VALIDATED", "projectId", project,
            "date", LocalDateTime.now().plusDays(inDays).withNano(0).toString()), Map.class);
    }
}
