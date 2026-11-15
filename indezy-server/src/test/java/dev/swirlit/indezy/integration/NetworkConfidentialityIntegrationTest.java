package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The confidentiality check over HTTP finds Alice's client, ESN and contact in her text, and nothing of hers for Bob. */
class NetworkConfidentialityIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void closeTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, false);
    }

    private void join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> check(Account account, String text) {
        return call(account, HttpMethod.POST, "/network/confidentiality", Map.of("text", text), List.class).getBody();
    }

    @Test
    void findsTheWorkspacesNamesAsWholeWords() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        join(bob);
        Object client = call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Société Générale", "city", "Paris", "isFinal", true,
            "freelanceId", alice.freelanceId()), Map.class).getBody().get("id");
        call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Alten", "city", "Boulogne", "isFinal", false,
            "freelanceId", alice.freelanceId()), Map.class);
        call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Hélène", "lastName", "Durand", "clientId", client,
            "freelanceId", alice.freelanceId()), Map.class);

        String text = "Mission chez SOCIETE GENERALE via Alten, avec Helene Durand. Rien sur Altenative.";
        assertThat(check(alice, text)).extracting(match -> match.get("found"))
            .containsExactlyInAnyOrder("SOCIETE GENERALE", "Alten", "Helene Durand");
        assertThat(check(alice, text)).filteredOn(match -> "SOCIETE GENERALE".equals(match.get("found"))).singleElement()
            .satisfies(match -> assertThat(match).containsEntry("kind", "CLIENT").containsEntry("city", "Paris"));
        assertThat(check(alice, text)).filteredOn(match -> "Alten".equals(match.get("found"))).singleElement()
            .satisfies(match -> assertThat(match).containsEntry("kind", "ESN"));
        assertThat(check(alice, "Un retour sur Kafka")).isEmpty();
        assertThat(check(bob, text)).isEmpty();
        assertThat(call(alice, HttpMethod.POST, "/network/confidentiality", Map.of("text", "x".repeat(3001)), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
