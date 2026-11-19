package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The About section over HTTP: Alice writes a few words and lists her won missions, Bob reads them on her page (role, stack,
 * period, never the client), not a mission still in the pipeline; hidden again, the missions go.
 */
class NetworkAboutIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    private void project(String role, String status, String start) {
        Map<String, Object> body = new HashMap<>(Map.of("role", role, "dailyRate", 650, "status", status, "clientId", aliceClientId,
            "techStack", "Java, Kafka; Spring Boot", "durationInMonths", 6));
        body.put("startDate", start);
        call(alice, HttpMethod.POST, "/projects", body, Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void showsTheAboutSectionAndTheWonMissions() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String handle = join(alice);
        join(bob);
        project("Lead Java", "WON", "2025-01-06");
        project("Architecte Kafka", "WON", "2026-02-02");
        project("En cours de discussion", "INTERVIEW", "2026-12-01");

        Map<String, Object> page = call(bob, HttpMethod.GET, "/network/members/" + handle + "/about", null, Map.class).getBody();
        assertThat(page).containsEntry("about", null);
        assertThat((List<Object>) page.get("missions")).isEmpty();

        Map<String, Object> saved = call(alice, HttpMethod.PUT, "/network/membership/about", Map.of("about", "  Architecte Java, 12 ans.  ",
            "showMissions", true), Map.class).getBody();
        assertThat(saved).containsEntry("about", "Architecte Java, 12 ans.").containsEntry("showMissions", true);
        assertThat(call(alice, HttpMethod.PUT, "/network/membership/about", Map.of("about", "x".repeat(1001), "showMissions", true),
            String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        page = call(bob, HttpMethod.GET, "/network/members/" + handle + "/about", null, Map.class).getBody();
        assertThat(page).containsEntry("about", "Architecte Java, 12 ans.");
        List<Map<String, Object>> missions = (List<Map<String, Object>>) page.get("missions");
        assertThat(missions).extracting(mission -> mission.get("role")).containsExactly("Architecte Kafka", "Lead Java");
        assertThat(missions.get(0)).containsEntry("stack", List.of("Java", "Kafka", "Spring Boot")).containsEntry("start", "2026-02-02")
            .doesNotContainKey("client");
        assertThat(call(alice, HttpMethod.GET, "/network/membership/about", null, Map.class).getBody()).containsEntry("showMissions", true);

        call(alice, HttpMethod.PUT, "/network/membership/about", Map.of("about", " ", "showMissions", false), Map.class);
        page = call(bob, HttpMethod.GET, "/network/members/" + handle + "/about", null, Map.class).getBody();
        assertThat(page).containsEntry("about", null);
        assertThat((List<Object>) page.get("missions")).isEmpty();
    }
}
