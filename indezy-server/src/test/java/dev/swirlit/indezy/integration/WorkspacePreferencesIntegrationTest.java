package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
/** Workspace preferences (target rate, inactivity and follow-up periods) stay with the account that sets them. */
class WorkspacePreferencesIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    void targetRate_ShouldDefaultNewSeasonsWithoutChangingExistingOnesOrOtherAccounts() {
        Map profile = call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        profile.put("targetDailyRate", 650);
        assertThat(call(alice, HttpMethod.PUT, "/users/profile", profile, Map.class).getBody())
            .containsEntry("targetDailyRate", 650);
        assertThat(call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody())
            .containsEntry("targetDailyRate", 650);
        call(alice, HttpMethod.PUT, "/users/preferences", Map.of("theme", "dark"), Map.class);
        assertThat(call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody())
            .containsEntry("targetDailyRate", 650);
        assertThat(call(bob, HttpMethod.GET, "/users/profile", null, Map.class).getBody().get("targetDailyRate"))
            .isNull();
        assertThat(call(alice, HttpMethod.GET, "/projects/stats/dashboard/" + alice.freelanceId(), null, Map.class).getBody())
            .containsEntry("targetDailyRate", 650).containsEntry("averageObtainedDailyRate", null);
        assertThat(call(bob, HttpMethod.GET, "/projects/stats/dashboard/" + bob.freelanceId(), null, Map.class).getBody()
            .get("targetDailyRate")).isNull();

        Map season = call(alice, HttpMethod.POST, "/seasons",
            Map.of("name", "Default target", "startDate", LocalDate.now().toString(), "freelanceId", alice.freelanceId()),
            Map.class).getBody();
        assertThat(season).containsEntry("targetDailyRate", 650);
        Map override = call(alice, HttpMethod.POST, "/seasons",
            Map.of("name", "Custom target", "startDate", LocalDate.now().toString(), "freelanceId", alice.freelanceId(),
                "targetDailyRate", 700), Map.class).getBody();
        assertThat(override).containsEntry("targetDailyRate", 700);

        profile.put("targetDailyRate", null);
        assertThat(call(alice, HttpMethod.PUT, "/users/profile", profile, Map.class).getBody().get("targetDailyRate"))
            .isNull();
        assertThat(call(alice, HttpMethod.GET, "/seasons/by-freelance/" + alice.freelanceId(), null, List.class).getBody())
            .anySatisfy(listed -> {
                assertThat(((Map<?, ?>) listed).get("id")).isEqualTo(season.get("id"));
                assertThat(((Map<?, ?>) listed).get("targetDailyRate")).isEqualTo(650);
            });
        Map noTarget = call(alice, HttpMethod.POST, "/seasons",
            Map.of("name", "No target", "startDate", LocalDate.now().toString(), "freelanceId", alice.freelanceId()),
            Map.class).getBody();
        assertThat(noTarget.get("targetDailyRate")).isNull();
    }

    @Test
    void targetRate_ShouldRejectNonPositiveFractionalAndOversizedValues() {
        Map profile = call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        for (Number rate : List.of(0, -1, 650.5, 2147483648L)) {
            profile.put("targetDailyRate", rate);
            assertThat(call(alice, HttpMethod.PUT, "/users/profile", profile, String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(call(alice, HttpMethod.POST, "/seasons",
                Map.of("name", "Invalid target", "startDate", LocalDate.now().toString(),
                    "freelanceId", alice.freelanceId(), "targetDailyRate", rate), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void inactivityPreference_ShouldPersistAndApplyToOnlyItsOwnWorkspace() {
        assertThat(call(alice, HttpMethod.GET, "/users/preferences", null, Map.class).getBody())
            .containsEntry("inactivityDays", 14);
        assertThat(call(alice, HttpMethod.PUT, "/users/preferences", Map.of("inactivityDays", 30), Map.class).getBody())
            .containsEntry("inactivityDays", 30);
        assertThat(call(alice, HttpMethod.GET, "/users/preferences", null, Map.class).getBody())
            .containsEntry("inactivityDays", 30);
        assertThat(call(bob, HttpMethod.GET, "/users/preferences", null, Map.class).getBody())
            .containsEntry("inactivityDays", 14);
        assertThat(call(alice, HttpMethod.GET, "/projects/kanban/" + alice.freelanceId(), null, Map.class).getBody())
            .containsEntry("inactivityDays", 30);
        assertThat(call(bob, HttpMethod.GET, "/projects/kanban/" + bob.freelanceId(), null, Map.class).getBody())
            .containsEntry("inactivityDays", 14);

        ResponseEntity<Map> project = call(alice, HttpMethod.POST, "/projects",
            Map.of("role", "Developer", "dailyRate", 600, "clientId", aliceClientId, "freelanceId", alice.freelanceId()),
            Map.class);
        assertThat(project.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long projectId = ((Number) project.getBody().get("id")).longValue();
        jdbcTemplate.update("UPDATE projects SET updated_at = ? WHERE id = ?", LocalDateTime.now().minusDays(10), projectId);
        String statsPath = "/projects/stats/dashboard/" + alice.freelanceId();
        assertThat((List<?>) call(alice, HttpMethod.GET, statsPath, null, Map.class).getBody().get("staleOpportunities"))
            .isEmpty();

        call(alice, HttpMethod.PUT, "/users/preferences", Map.of("inactivityDays", 7), Map.class);
        assertThat((List<?>) call(alice, HttpMethod.GET, statsPath, null, Map.class).getBody().get("staleOpportunities"))
            .hasSize(1);
        assertThat(call(alice, HttpMethod.PUT, "/users/preferences", Map.of("theme", "dark"), Map.class).getBody())
            .containsEntry("inactivityDays", 7);
        assertThat(call(alice, HttpMethod.GET, "/projects/kanban/" + alice.freelanceId(), null, Map.class).getBody())
            .containsEntry("inactivityDays", 7);
    }

    @Test
    void inactivityPreference_ShouldRejectInvalidValuesAndDefaultLegacyAccounts() {
        for (Number days : List.of(-1, 0, 366, 7.5)) {
            assertThat(call(alice, HttpMethod.PUT, "/users/preferences", Map.of("inactivityDays", days), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        jdbcTemplate.update("UPDATE users SET inactivity_days = NULL WHERE email = (SELECT email FROM freelances WHERE id = ?)",
            alice.freelanceId());
        assertThat(call(alice, HttpMethod.GET, "/users/preferences", null, Map.class).getBody())
            .containsEntry("inactivityDays", 14);
        assertThat(call(alice, HttpMethod.GET, "/projects/kanban/" + alice.freelanceId(), null, Map.class).getBody())
            .containsEntry("inactivityDays", 14);
    }

    @Test
    void followUpPreference_ShouldPersistRejectInvalidValuesAndDefaultLegacyAccounts() {
        assertThat(call(alice, HttpMethod.GET, "/users/preferences", null, Map.class).getBody()).containsEntry("followUpDays", 5);
        assertThat(call(alice, HttpMethod.PUT, "/users/preferences", Map.of("followUpDays", 10), Map.class).getBody())
            .containsEntry("followUpDays", 10);
        for (Number days : List.of(0, 61, 2.5)) {
            assertThat(call(alice, HttpMethod.PUT, "/users/preferences", Map.of("followUpDays", days), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(call(bob, HttpMethod.GET, "/users/preferences", null, Map.class).getBody()).containsEntry("followUpDays", 5);
        jdbcTemplate.update("UPDATE users SET follow_up_days = NULL WHERE email = (SELECT email FROM freelances WHERE id = ?)",
            alice.freelanceId());
        assertThat(call(alice, HttpMethod.GET, "/users/preferences", null, Map.class).getBody()).containsEntry("followUpDays", 5);
    }
}
