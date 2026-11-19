package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The pinned post over HTTP: Alice pins one of her posts, another replaces it, she unpins it; deleting it unpins it; never Bob's. */
class NetworkPinnedPostIntegrationTest extends TwoAccountsIntegrationTest {

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

    private Object pinned(Account viewer, String handle) {
        return call(viewer, HttpMethod.GET, "/network/members/" + handle + "/page", null, Map.class).getBody().get("pinnedPostId");
    }

    @Test
    void pinsOnePostAtATop() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String handle = join(alice);
        join(bob);
        Object first = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Ma présentation"), Map.class).getBody().get("id");
        Object second = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Mon offre"), Map.class).getBody().get("id");
        assertThat(pinned(bob, handle)).isNull();

        assertThat(call(alice, HttpMethod.PUT, "/network/posts/" + first + "/pin", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(pinned(bob, handle)).isEqualTo(first);
        call(alice, HttpMethod.PUT, "/network/posts/" + second + "/pin", null, String.class);
        assertThat(pinned(bob, handle)).isEqualTo(second);
        // Unpinning another post leaves the pinned one; Bob cannot pin Alice's.
        call(alice, HttpMethod.DELETE, "/network/posts/" + first + "/pin", null, String.class);
        assertThat(pinned(alice, handle)).isEqualTo(second);
        assertThat(call(bob, HttpMethod.PUT, "/network/posts/" + first + "/pin", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        call(alice, HttpMethod.DELETE, "/network/posts/" + second + "/pin", null, String.class);
        assertThat(pinned(alice, handle)).isNull();

        call(alice, HttpMethod.PUT, "/network/posts/" + first + "/pin", null, String.class);
        call(alice, HttpMethod.DELETE, "/network/posts/" + first, null, String.class);
        assertThat(pinned(bob, handle)).isNull();
    }
}
