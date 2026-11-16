package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The notification centre over HTTP: Alice hears of Bob's and Carol's follows, likes, comments, mentions and reposts,
 * grouped as they read; once read, none is unread; a kind turned off, or a member blocked, tells nothing more.
 */
class NetworkNotificationsIntegrationTest extends TwoAccountsIntegrationTest {

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
    private List<Map<String, Object>> notifications(Account account) {
        return call(account, HttpMethod.GET, "/network/notifications", null, List.class).getBody();
    }

    private long unread(Account account) {
        return ((Number) call(account, HttpMethod.GET, "/network/notifications/unread", null, Map.class).getBody().get("unread")).longValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tellsWhatMembersDidGroupedAndMarkedRead() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        Account carol = register("carol");
        join(carol);
        Object post = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Une question sur Kafka"), Map.class).getBody().get("id");

        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        call(bob, HttpMethod.DELETE, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        call(bob, HttpMethod.PUT, "/network/posts/" + post + "/like", null, Map.class);
        call(carol, HttpMethod.PUT, "/network/posts/" + post + "/like", null, Map.class);
        call(alice, HttpMethod.PUT, "/network/posts/" + post + "/like", null, Map.class);
        Object comment = call(bob, HttpMethod.POST, "/network/posts/" + post + "/comments", Map.of("text", "Voici"), Map.class).getBody().get("id");
        call(alice, HttpMethod.POST, "/network/posts/" + post + "/comments", Map.of("text", "Merci @" + bobHandle, "parentId", comment), Map.class);
        call(carol, HttpMethod.POST, "/network/posts/" + post + "/reposts", Map.of("text", "Voir @" + aliceHandle), Map.class);

        assertThat(unread(alice)).isEqualTo(6);
        List<Map<String, Object>> groups = notifications(alice);
        assertThat(groups).extracting(group -> group.get("type")).containsExactly("MENTION", "REPOST", "COMMENT", "LIKE", "FOLLOW");
        Map<String, Object> likes = groups.get(3);
        assertThat(likes).containsEntry("count", 2).containsEntry("postId", post).containsEntry("postExcerpt", "Une question sur Kafka")
            .containsEntry("read", false);
        assertThat((List<Map<String, Object>>) likes.get("actors")).extracting(card -> card.get("firstName")).containsExactly("carol", "bob");
        assertThat(notifications(bob)).extracting(group -> group.get("type")).containsExactly("MENTION", "REPLY");

        call(alice, HttpMethod.POST, "/network/notifications/read", null, String.class);
        assertThat(unread(alice)).isZero();
        assertThat(notifications(alice)).allSatisfy(group -> assertThat(group).containsEntry("read", true));

        // A kind turned off, or a member blocked, tells nothing more.
        assertThat(call(alice, HttpMethod.PUT, "/network/notifications/settings", Map.of("off", List.of("LIKE", "LIKE", "REPOST")), Map.class)
            .getBody()).containsEntry("off", List.of("LIKE", "REPOST"));
        Object second = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Deuxième"), Map.class).getBody().get("id");
        call(bob, HttpMethod.PUT, "/network/posts/" + second + "/like", null, Map.class);
        assertThat(unread(alice)).isZero();
        call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/block", null, Map.class);
        call(bob, HttpMethod.POST, "/network/posts/" + second + "/comments", Map.of("text", "Encore"), String.class);
        assertThat(unread(alice)).isZero();
        assertThat(call(alice, HttpMethod.GET, "/network/notifications/settings", null, Map.class).getBody())
            .containsEntry("off", List.of("LIKE", "REPOST"));
        assertThat(notifications(alice)).noneSatisfy(group -> assertThat((List<Map<String, Object>>) group.get("actors"))
            .anySatisfy(card -> assertThat(card).containsEntry("handle", bobHandle)));
    }
}
