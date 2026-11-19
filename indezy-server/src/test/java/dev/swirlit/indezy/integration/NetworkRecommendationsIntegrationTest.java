package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recommendations on a member's page over HTTP: Bob reads the testimonials Alice approved, not those waiting or hidden;
 * once her card is private, only her followers do.
 */
class NetworkRecommendationsIntegrationTest extends TwoAccountsIntegrationTest {

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

    @SuppressWarnings("unchecked")
    private Object written(String author, String text) {
        Map<String, Object> link = call(alice, HttpMethod.POST, "/testimonials", Map.of("recipientName", author), Map.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Testimonial-Token", (String) link.get("token"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        exchange("/public/testimonials", HttpMethod.POST, new HttpEntity<>(Map.of("authorName", author, "authorTitle", "CTO, Acme",
            "text", text, "consent", true), headers), Map.class);
        return ((Map<String, Object>) link.get("testimonial")).get("id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void showsTheApprovedTestimonialsToMembers() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String handle = join(alice);
        join(bob);
        Object approved = written("Léa Martin", "Une consultante rigoureuse, qui a mené la refonte en temps et en heure.");
        written("Paul Durand", "Un témoignage reçu, en attente de relecture par Alice.");
        call(alice, HttpMethod.PUT, "/testimonials/" + approved + "/status", Map.of("status", "APPROVED"), Map.class);

        String path = "/network/members/" + handle + "/recommendations";
        List<Map<String, Object>> shown = call(bob, HttpMethod.GET, path, null, List.class).getBody();
        assertThat(shown).singleElement().satisfies(found -> assertThat(found).containsEntry("authorName", "Léa Martin")
            .containsEntry("authorTitle", "CTO, Acme"));
        assertThat(call(alice, HttpMethod.GET, path, null, List.class).getBody()).hasSize(1);
        assertThat(call(bob, HttpMethod.GET, "/network/members/nobody_here/recommendations", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // A private card's recommendations reach its followers only.
        call(alice, HttpMethod.PUT, "/network/membership/private?private=true", null, Map.class);
        assertThat(call(bob, HttpMethod.GET, "/network/members/" + handle + "/recommendations", null, List.class).getBody()).isEmpty();
    }
}
