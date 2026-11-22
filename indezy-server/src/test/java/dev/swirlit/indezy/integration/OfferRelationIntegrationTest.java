package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A feed offer from a company Alice won a mission with says so; Bob's feed does not. */
class OfferRelationIntegrationTest extends OfferIntegrationTestSupport {

    @Test
    @SuppressWarnings("unchecked")
    void marksTheOffersOfACompanyWorkedWith() {
        assertThat(call(alice, HttpMethod.GET, "/offers/" + paris.getId(), null).getBody()).doesNotContainKey("relation");

        Map<String, Object> esn = call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "EXEMPLE  Conseil", "city", "Paris",
            "isFinal", false, "freelanceId", alice.freelanceId())).getBody();
        Map<String, Object> client = call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Airbus", "city", "Toulouse",
            "isFinal", true, "freelanceId", alice.freelanceId())).getBody();
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650, "status", "WON", "clientId", client.get("id"),
            "middlemanId", esn.get("id"), "freelanceId", alice.freelanceId(), "startDate", "2026-01-05", "durationInMonths", 6));

        // Paris is published by "Exemple Conseil": the ESN of Alice's mission, whatever the spelling.
        assertThat((Map<String, Object>) call(alice, HttpMethod.GET, "/offers/" + paris.getId(), null).getBody().get("relation"))
            .containsEntry("workedWith", true);
        assertThat(call(bob, HttpMethod.GET, "/offers/" + paris.getId(), null).getBody()).doesNotContainKey("relation");
    }
}
