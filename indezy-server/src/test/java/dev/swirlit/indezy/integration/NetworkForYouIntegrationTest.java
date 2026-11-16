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

/** For you over HTTP: Carol's post on a tag Alice follows ranks above Bob's, Alice's own and Dave's, muted, are left out. */
class NetworkForYouIntegrationTest extends TwoAccountsIntegrationTest {

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

    private Object post(Account account, String text) {
        return call(account, HttpMethod.POST, "/network/posts", Map.of("text", text), Map.class).getBody().get("id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ranksWhatTheReaderFollowsFirst() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        join(bob);
        Account carol = register("carol");
        join(carol);
        Account dave = register("dave");
        String daveHandle = join(dave);
        String tag = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        call(alice, HttpMethod.PUT, "/network/tags/" + tag + "/follow", null, List.class);
        Object carolsPost = post(carol, "Retour sur #" + tag);
        Object bobsPost = post(bob, "Bonjour " + UUID.randomUUID());
        Object mine = post(alice, "Le mien #" + tag);
        Object davesPost = post(dave, "Encore #" + tag);
        call(alice, HttpMethod.PUT, "/network/members/" + daveHandle + "/mute", null, Map.class);

        List<Object> ids = ((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/network/feed/for-you", null, Map.class).getBody()
            .get("posts")).stream().map(found -> found.get("id")).toList();
        assertThat(ids).contains(carolsPost, bobsPost).doesNotContain(mine, davesPost);
        assertThat(ids.indexOf(carolsPost)).isLessThan(ids.indexOf(bobsPost));
    }
}
