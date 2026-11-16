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

/** Useful over HTTP: Bob and Carol find Alice's older post useful; a tag page then lists it first when asked. */
class NetworkUsefulIntegrationTest extends TwoAccountsIntegrationTest {

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
    private List<Object> tagPosts(Account reader, String tag, String sort) {
        Map<String, Object> page = (Map<String, Object>) call(reader, HttpMethod.GET, "/network/tags/" + tag + "?sort=" + sort, null,
            Map.class).getBody().get("posts");
        return ((List<Map<String, Object>>) page.get("posts")).stream().map(post -> post.get("id")).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findsPostsUsefulAndListsTheMostUsefulFirst() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        join(bob);
        Account carol = register("carol");
        join(carol);
        String tag = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Object older = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Retour sur Kafka #" + tag, "kind", "EXPERIENCE"),
            Map.class).getBody().get("id");
        Object newer = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Bonjour #" + tag), Map.class).getBody().get("id");

        Map<String, Object> marked = call(bob, HttpMethod.PUT, "/network/posts/" + older + "/useful", null, Map.class).getBody();
        assertThat((Map<String, Object>) marked.get("useful")).containsEntry("count", 1).containsEntry("mine", true);
        assertThat((Map<String, Object>) call(bob, HttpMethod.PUT, "/network/posts/" + older + "/useful", null, Map.class).getBody()
            .get("useful")).containsEntry("count", 1);
        call(carol, HttpMethod.PUT, "/network/posts/" + older + "/useful", null, Map.class);
        assertThat(call(alice, HttpMethod.PUT, "/network/posts/" + older + "/useful", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(tagPosts(bob, tag, "recent")).containsExactly(newer, older);
        assertThat(tagPosts(bob, tag, "useful")).containsExactly(older, newer);
        Map<String, Object> unmarked = call(carol, HttpMethod.DELETE, "/network/posts/" + older + "/useful", null, Map.class).getBody();
        assertThat((Map<String, Object>) unmarked.get("useful")).containsEntry("count", 1).containsEntry("mine", false);
        call(bob, HttpMethod.DELETE, "/network/posts/" + older + "/useful", null, Map.class);
        assertThat(tagPosts(bob, tag, "useful")).containsExactly(newer, older);
    }
}
