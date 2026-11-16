package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spam score over HTTP: Bob's repeated post and his comment full of links are held, not published; the administrator
 * publishes one, rejects the other (Bob told), and cannot publish a comment whose post is gone.
 */
@TestPropertySource(properties = "indezy.admin.emails=" + AdminIntegrationTest.CHIEF)
class NetworkSpamIntegrationTest extends TwoAccountsIntegrationTest {

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
    private List<Map<String, Object>> held(Account admin, String text) {
        List<Map<String, Object>> held = (List<Map<String, Object>>) call(admin, HttpMethod.GET, "/admin/network/reports", null, Map.class)
            .getBody().get("held");
        return held.stream().filter(item -> text.equals(item.get("text"))).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void holdsWhatScoresForAnAdministratorToPublishOrReject() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        String bobHandle = join(bob);
        Account admin = chief();
        String text = "Formation Kafka " + UUID.randomUUID();
        assertThat(call(bob, HttpMethod.POST, "/network/posts", Map.of("text", text), Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map> again = call(bob, HttpMethod.POST, "/network/posts", Map.of("text", text.toUpperCase()), Map.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(again.getBody()).containsEntry("held", true);
        Object post = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Une question"), Map.class).getBody().get("id");
        String links = "Voir https://a.example https://b.example https://c.example " + UUID.randomUUID();
        assertThat(call(bob, HttpMethod.POST, "/network/posts/" + post + "/comments", Map.of("text", links), Map.class).getStatusCode())
            .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(call(bob, HttpMethod.GET, "/network/posts/" + post + "/comments", null, List.class).getBody()).isEmpty();

        // Only administrators see what is held, with why.
        assertThat(call(bob, HttpMethod.PUT, "/admin/network/held/1?publish=true", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        Map<String, Object> repeated = held(admin, text.toUpperCase()).getFirst();
        assertThat((List<String>) repeated.get("reasons")).contains("REPEATED");
        assertThat((Map<String, Object>) repeated.get("author")).containsEntry("handle", bobHandle);
        Map<String, Object> linked = held(admin, links).getFirst();
        assertThat((List<String>) linked.get("reasons")).contains("LINKS", "NEW_MEMBER_LINK");
        assertThat(linked).containsEntry("postId", post);

        // Published: Bob's comment appears under Alice's post. Rejected: Bob is told it was removed.
        call(admin, HttpMethod.PUT, "/admin/network/held/" + linked.get("id") + "?publish=true", null, Map.class);
        assertThat(call(alice, HttpMethod.GET, "/network/posts/" + post + "/comments", null, List.class).getBody()).singleElement()
            .satisfies(comment -> assertThat((Map<String, Object>) comment).containsEntry("text", links));
        assertThat(held(admin, links)).isEmpty();
        call(admin, HttpMethod.PUT, "/admin/network/held/" + repeated.get("id") + "?publish=false", null, Map.class);
        assertThat(held(admin, text.toUpperCase())).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/network/notifications", null, List.class).getBody())
            .anySatisfy(group -> assertThat((Map<String, Object>) group).containsEntry("type", "CONTENT_REMOVED"));
        assertThat(call(admin, HttpMethod.PUT, "/admin/network/held/" + repeated.get("id") + "?publish=true", null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Held under a post since deleted, it can only be rejected.
        String late = "Encore https://a.example https://b.example https://c.example " + UUID.randomUUID();
        call(bob, HttpMethod.POST, "/network/posts/" + post + "/comments", Map.of("text", late), Map.class);
        call(alice, HttpMethod.DELETE, "/network/posts/" + post, null, String.class);
        Object stale = held(admin, late).getFirst().get("id");
        ResponseEntity<Map> refused = call(admin, HttpMethod.PUT, "/admin/network/held/" + stale + "?publish=true", null, Map.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody()).containsEntry("message", "It can no longer be published: reject it");
        assertThat(held(admin, late)).hasSize(1);
        call(admin, HttpMethod.PUT, "/admin/network/held/" + stale + "?publish=false", null, Map.class);
        assertThat(held(admin, late)).isEmpty();
    }

    private Account chief() {
        ResponseEntity<Map> signedIn = login(AdminIntegrationTest.CHIEF);
        if (signedIn.getStatusCode() != HttpStatus.OK) {
            registerAs(AdminIntegrationTest.CHIEF);
            signedIn = login(AdminIntegrationTest.CHIEF);
        }
        Map<?, ?> user = (Map<?, ?>) signedIn.getBody().get("user");
        return new Account((String) signedIn.getBody().get("token"), ((Number) user.get("id")).longValue());
    }

    private ResponseEntity<Map> login(String email) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        return post("/auth/login", request);
    }
}
