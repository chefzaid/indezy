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
 * The member network over HTTP: closed until an administrator opens it; Alice joins with a handle no other member
 * holds, shows only the fields she chose, and only members see her card, until she leaves.
 */
class NetworkIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void closeTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, false);
    }

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Test
    void staysClosedUntilAnAdministratorOpensIt() {
        assertThat(call(alice, HttpMethod.GET, "/network/membership", null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        assertThat(call(alice, HttpMethod.GET, "/network/membership", null, Map.class).getBody()).containsEntry("joined", false)
            .containsEntry("card", null).hasEntrySatisfying("handle", handle -> assertThat((String) handle).startsWith("alice"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void joinsWithAUniqueHandleAndShowsTheChosenFieldsToMembersOnly() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        Map<String, Object> profile = call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        profile.putAll(Map.of("position", "Architecte Java", "city", "Lyon", "skills", List.of("Java", "Kafka"),
            "languages", List.of("Français", "Anglais")));
        call(alice, HttpMethod.PUT, "/users/profile", profile, Map.class);

        String handle = unique("alice");
        assertThat(call(alice, HttpMethod.GET, "/network/handles/" + handle.toUpperCase(), null, Map.class).getBody())
            .containsEntry("handle", handle).containsEntry("available", true);
        assertThat(call(alice, HttpMethod.GET, "/network/handles/admin", null, Map.class).getBody()).containsEntry("available", false);
        assertThat(call(alice, HttpMethod.PUT, "/network/membership", Map.of("handle", "Not A Handle", "fields", ALL_SHOWN), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.PUT, "/network/membership", Map.of("handle", "support", "fields", ALL_SHOWN), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Map<String, Object> fields = new HashMap<>(ALL_SHOWN);
        fields.putAll(Map.of("location", false, "languages", false, "remote", true));
        Map<String, Object> joined = call(alice, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", fields), Map.class)
            .getBody();
        assertThat(joined).containsEntry("joined", true).containsEntry("handle", handle);
        assertThat((Map<String, Object>) joined.get("card")).containsEntry("firstName", "alice").containsEntry("headline", "Architecte Java")
            .containsEntry("stack", List.of("Java", "Kafka")).containsEntry("city", null).containsEntry("remote", true)
            .containsEntry("languages", List.of());
        // Another member cannot take the handle; Bob sees no card before joining, then sees Alice's.
        assertThat(call(bob, HttpMethod.GET, "/network/handles/" + handle, null, Map.class).getBody()).containsEntry("available", false);
        assertThat(call(bob, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(bob, HttpMethod.GET, "/network/members/" + handle, null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        call(bob, HttpMethod.PUT, "/network/membership", Map.of("handle", unique("bob"), "fields", ALL_SHOWN), Map.class);
        assertThat(call(bob, HttpMethod.GET, "/network/members/" + handle, null, Map.class).getBody())
            .containsEntry("handle", handle).containsEntry("headline", "Architecte Java").containsEntry("city", null);
        assertThat(call(bob, HttpMethod.GET, "/network/members/nobody_here", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, "/users/export", null, String.class).getBody()).contains("\"networkMembership\"").contains(handle);

        // Leaving hides the card at once, and keeps the handle hers for her return.
        assertThat(call(alice, HttpMethod.DELETE, "/network/membership", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(bob, HttpMethod.GET, "/network/members/" + handle, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.GET, "/network/handles/" + handle, null, Map.class).getBody()).containsEntry("available", false);
        Map<String, Object> left = call(alice, HttpMethod.GET, "/network/membership", null, Map.class).getBody();
        assertThat(left).containsEntry("joined", false).containsEntry("handle", handle);
        assertThat((Map<String, Object>) left.get("fields")).containsEntry("location", false).containsEntry("remote", true);
        assertThat(call(alice, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", fields), Map.class).getBody())
            .containsEntry("joined", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void followsAMemberSeesTheSharedSkillsAndRemovesAFollower() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        for (Account account : List.of(alice, bob)) {
            Map<String, Object> profile = call(account, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
            profile.put("skills", account == alice ? List.of("Java", "Kafka", "SQL") : List.of("kafka", "Java", "Go"));
            call(account, HttpMethod.PUT, "/users/profile", profile, Map.class);
        }
        String aliceHandle = unique("alice");
        String bobHandle = unique("bob");
        assertThat(call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        call(alice, HttpMethod.PUT, "/network/membership", Map.of("handle", aliceHandle, "fields", ALL_SHOWN), Map.class);
        call(bob, HttpMethod.PUT, "/network/membership", Map.of("handle", bobHandle, "fields", ALL_SHOWN), Map.class);

        assertThat(call(bob, HttpMethod.GET, "/network/members/" + aliceHandle + "/page", null, Map.class).getBody())
            .containsEntry("followers", 0).containsEntry("followed", false).containsEntry("own", false)
            .containsEntry("sharedSkills", List.of("Java", "Kafka"));
        assertThat(call(bob, HttpMethod.PUT, "/network/members/" + bobHandle + "/follow", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> followed = call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class).getBody();
        assertThat(followed).containsEntry("followers", 1).containsEntry("followed", true).containsEntry("followsYou", false);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        assertThat(call(alice, HttpMethod.GET, "/network/members/" + aliceHandle + "/page", null, Map.class).getBody())
            .containsEntry("own", true).containsEntry("followers", 1).containsEntry("sharedSkills", List.of());
        assertThat(call(alice, HttpMethod.GET, "/network/members/" + bobHandle + "/page", null, Map.class).getBody())
            .containsEntry("following", 1).containsEntry("followsYou", true);
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/network/members/" + aliceHandle + "/followers", null, List.class)
            .getBody()).singleElement().satisfies(card -> assertThat(card).containsEntry("handle", bobHandle));
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/network/members/" + bobHandle + "/following", null, List.class)
            .getBody()).singleElement().satisfies(card -> assertThat(card).containsEntry("handle", aliceHandle));

        // A member who leaves leaves the counts; Alice removes Bob as a follower, then Bob unfollows nobody.
        call(bob, HttpMethod.DELETE, "/network/membership", null, String.class);
        assertThat(call(alice, HttpMethod.GET, "/network/members/" + aliceHandle + "/page", null, Map.class).getBody())
            .containsEntry("followers", 0);
        call(bob, HttpMethod.PUT, "/network/membership", Map.of("handle", bobHandle, "fields", ALL_SHOWN), Map.class);
        assertThat(call(alice, HttpMethod.DELETE, "/network/followers/" + bobHandle, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(bob, HttpMethod.GET, "/network/members/" + aliceHandle + "/page", null, Map.class).getBody())
            .containsEntry("followed", false).containsEntry("followers", 0);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        assertThat(call(bob, HttpMethod.DELETE, "/network/members/" + aliceHandle + "/follow", null, Map.class).getBody())
            .containsEntry("followed", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tellsTheAvailabilityFromTheWonMissionOrAsSet() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        java.time.LocalDate start = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris")).minusMonths(1);
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650, "status", "WON", "clientId", aliceClientId,
            "startDate", start.toString(), "durationInMonths", 6), Map.class);
        String handle = unique("alice");
        Map<String, Object> joined = call(alice, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN),
            Map.class).getBody();
        assertThat((Map<String, Object>) joined.get("availability")).containsEntry("mode", "AUTO");
        assertThat((Map<String, Object>) joined.get("card")).containsEntry("availability", "ON_MISSION")
            .containsEntry("availabilityDate", start.plusMonths(6).minusDays(1).toString());

        String later = start.plusMonths(3).toString();
        Map<String, Object> available = call(alice, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN,
            "availability", Map.of("mode", "AVAILABLE", "date", later)), Map.class).getBody();
        assertThat((Map<String, Object>) available.get("card")).containsEntry("availability", "AVAILABLE").containsEntry("availabilityDate", later);
        Map<String, Object> listening = call(alice, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN,
            "availability", Map.of("mode", "LISTENING", "date", later)), Map.class).getBody();
        assertThat((Map<String, Object>) listening.get("availability")).containsEntry("mode", "LISTENING").containsEntry("date", null);
        assertThat((Map<String, Object>) listening.get("card")).containsEntry("availability", "LISTENING");
        Map<String, Object> hidden = new HashMap<>(ALL_SHOWN);
        hidden.put("availability", false);
        assertThat((Map<String, Object>) call(alice, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", hidden),
            Map.class).getBody().get("card")).containsEntry("availability", null);
    }
}
