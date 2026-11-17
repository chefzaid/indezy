package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.config.RetiredUsefulReactions;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tag's posts the most liked first over HTTP: Bob and Carol react to Alice's older post, which then comes first when
 * asked. The Useful reaction is gone: its endpoint answers no more, and the reactions kept from it are removed.
 */
class NetworkTagLikedIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RetiredUsefulReactions retired;

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
    void listsATagsMostLikedPostsFirst() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        join(bob);
        Account carol = register("carol");
        join(carol);
        String tag = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Object older = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Retour sur Kafka #" + tag, "kind", "EXPERIENCE"),
            Map.class).getBody().get("id");
        Object newer = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Bonjour #" + tag), Map.class).getBody().get("id");

        call(bob, HttpMethod.PUT, "/network/posts/" + older + "/like", null, Map.class);
        call(carol, HttpMethod.PUT, "/network/posts/" + older + "/like?reaction=BRAVO", null, Map.class);
        assertThat(tagPosts(bob, tag, "recent")).containsExactly(newer, older);
        assertThat(tagPosts(bob, tag, "liked")).containsExactly(older, newer);
        call(bob, HttpMethod.DELETE, "/network/posts/" + older + "/like", null, Map.class);
        call(carol, HttpMethod.DELETE, "/network/posts/" + older + "/like", null, Map.class);
        assertThat(tagPosts(bob, tag, "liked")).containsExactly(newer, older);

        assertThat(call(bob, HttpMethod.PUT, "/network/posts/" + older + "/useful", null, String.class).getStatusCode().is4xxClientError())
            .isTrue();
        Map<String, Object> post = call(bob, HttpMethod.GET, "/network/posts/" + older, null, Map.class).getBody();
        assertThat(post).doesNotContainKey("useful");
    }

    @Test
    void removesTheUsefulReactionsKeptBefore() {
        retired.run(null);
        jdbc.execute("CREATE TABLE network_usefuls (id BIGINT PRIMARY KEY, post_id BIGINT, user_id BIGINT)");
        try {
            jdbc.update("INSERT INTO network_usefuls (id, post_id, user_id) VALUES (1, 10, 20), (2, 11, 20)");
            retired.run(null);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM network_usefuls", Integer.class)).isZero();
            // The table stays for the version still running.
            retired.run(null);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM network_usefuls", Integer.class)).isZero();
        } finally {
            jdbc.execute("DROP TABLE network_usefuls");
        }
    }
}
