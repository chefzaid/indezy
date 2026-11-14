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
}
