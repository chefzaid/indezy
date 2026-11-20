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

/** **Not interested** over HTTP: Bob hides one of Alice's posts from For you, then wants it back; an unknown post is not found. */
class NetworkNotInterestedIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private void join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Object> forYou(Account reader) {
        return ((List<Map<String, Object>>) call(reader, HttpMethod.GET, "/network/feed/for-you", null, Map.class).getBody().get("posts"))
            .stream().map(post -> post.get("id")).toList();
    }

    @Test
    void hidesAPostFromForYouAndBringsItBack() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        join(bob);
        Object first = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Retour #kafka"), Map.class).getBody().get("id");
        Object second = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Atelier #spark"), Map.class).getBody().get("id");
        assertThat(forYou(bob)).contains(first, second);

        assertThat(call(bob, HttpMethod.PUT, "/network/posts/" + first + "/not-interested", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        call(bob, HttpMethod.PUT, "/network/posts/" + first + "/not-interested", null, String.class);
        assertThat(forYou(bob)).doesNotContain(first).contains(second);
        // Alice's For you is hers alone.
        assertThat(call(bob, HttpMethod.PUT, "/network/posts/999999999/not-interested", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        call(bob, HttpMethod.DELETE, "/network/posts/" + first + "/not-interested", null, String.class);
        assertThat(forYou(bob)).contains(first, second);
        // A deleted post takes the dismissals with it.
        call(bob, HttpMethod.PUT, "/network/posts/" + second + "/not-interested", null, String.class);
        assertThat(call(alice, HttpMethod.DELETE, "/network/posts/" + second, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
