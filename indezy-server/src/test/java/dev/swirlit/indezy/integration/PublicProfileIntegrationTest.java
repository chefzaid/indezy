package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A public profile: chosen and shown by its owner, read by anyone with the link, gone when turned off. */
class PublicProfileIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void showsTheProfileItsOwnerTurnedOnToAnyoneWithTheLink() {
        Map<String, Object> profile = new HashMap<>(call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody());
        profile.putAll(Map.of("position", "Lead développeur Java", "city", "Paris", "bio", "Java and Kafka since 2012",
            "skills", List.of("Java", "Kafka"), "skillDetails", Map.of("Java", Map.of("level", "EXPERT", "years", 12)),
            "website", "https://alice.example", "linkedin", "javascript:alert(1)", "phone", "+33 6 00 00 00 00",
            "availabilityDate", LocalDate.now(ZoneId.of("Europe/Paris")).minusDays(1).toString()));
        assertThat(call(alice, HttpMethod.PUT, "/users/profile", profile, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(alice, HttpMethod.GET, "/users/public-profile", null, Map.class).getBody())
            .containsEntry("enabled", false).containsEntry("slug", null);
        assertThat(anonymous("/public/profiles/alice-martin").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(call(alice, HttpMethod.PUT, "/users/public-profile", Map.of("enabled", true, "slug", "Alice-Martin"), Map.class)
            .getBody()).containsEntry("enabled", true).containsEntry("slug", "alice-martin");
        ResponseEntity<Map> shown = anonymous("/public/profiles/alice-martin");
        assertThat(shown.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(shown.getBody()).containsEntry("firstName", "alice").containsEntry("position", "Lead développeur Java")
            .containsEntry("city", "Paris").containsEntry("website", "https://alice.example").containsEntry("linkedin", null)
            .containsEntry("availableNow", true).doesNotContainKeys("email", "phone", "address", "id");
        assertThat((List<Map<String, Object>>) shown.getBody().get("skills")).first()
            .satisfies(skill -> assertThat(skill).containsEntry("name", "Java").containsEntry("level", "EXPERT").containsEntry("years", 12));

        // Bob cannot take Alice's address, nor show a profile without one.
        assertThat(call(bob, HttpMethod.PUT, "/users/public-profile", Map.of("enabled", true, "slug", "alice-martin"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(bob, HttpMethod.PUT, "/users/public-profile", Map.of("enabled", true), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        for (String invalid : List.of("ab", "-alice", "alice--martin", "alice_martin", "a".repeat(41))) {
            assertThat(call(bob, HttpMethod.PUT, "/users/public-profile", Map.of("enabled", false, "slug", invalid), String.class)
                .getStatusCode()).as(invalid).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        call(alice, HttpMethod.PUT, "/users/public-profile", Map.of("enabled", false, "slug", "alice-martin"), Map.class);
        assertThat(anonymous("/public/profiles/alice-martin").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.PUT, "/users/public-profile", Map.of("enabled", false, "slug", "bob-test"), Map.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> anonymous(String path) {
        return exchange(path, HttpMethod.GET, null, Map.class);
    }
}
