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

/** Dismissing notifications over HTTP: Alice deletes the likes of one post, then Bob's follow; the rest stays; never another's. */
class NetworkNotificationDismissIntegrationTest extends TwoAccountsIntegrationTest {

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
    private List<String> kinds(Account reader) {
        return ((List<Map<String, Object>>) call(reader, HttpMethod.GET, "/network/notifications", null, List.class).getBody()).stream()
            .map(group -> group.get("type") + ":" + group.get("postId")).toList();
    }

    @Test
    void deletesAGroupOfNotifications() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        join(bob);
        Object first = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Un"), Map.class).getBody().get("id");
        Object second = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Deux"), Map.class).getBody().get("id");
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        call(bob, HttpMethod.PUT, "/network/posts/" + first + "/like", null, Map.class);
        call(bob, HttpMethod.PUT, "/network/posts/" + second + "/like", null, Map.class);
        assertThat(kinds(alice)).contains("LIKE:" + first, "LIKE:" + second, "FOLLOW:null");

        assertThat(call(alice, HttpMethod.DELETE, "/network/notifications?type=LIKE&postId=" + first, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(kinds(alice)).doesNotContain("LIKE:" + first).contains("LIKE:" + second, "FOLLOW:null");
        call(alice, HttpMethod.DELETE, "/network/notifications?type=FOLLOW", null, String.class);
        assertThat(kinds(alice)).containsExactly("LIKE:" + second);
        // Bob deleting the same kind deletes nothing of Alice's; a kind unknown is refused.
        call(bob, HttpMethod.DELETE, "/network/notifications?type=LIKE&postId=" + second, null, String.class);
        assertThat(kinds(alice)).containsExactly("LIKE:" + second);
        assertThat(call(alice, HttpMethod.DELETE, "/network/notifications?type=NOPE", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
