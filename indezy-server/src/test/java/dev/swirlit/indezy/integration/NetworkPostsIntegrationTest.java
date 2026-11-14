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

/**
 * Posts over HTTP: Alice posts with #tags; Bob reads her posts in his feed once he follows her, not while he mutes her,
 * and on her page; Alice deletes her own post, never Bob's.
 */
class NetworkPostsIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void closeTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, false);
    }

    private String join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> posts(Account reader, String path) {
        return (List<Map<String, Object>>) call(reader, HttpMethod.GET, path, null, Map.class).getBody().get("posts");
    }

    @Test
    @SuppressWarnings("unchecked")
    void postsReadsTheFeedAndDeletes() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        assertThat(call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Bonjour"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        String aliceHandle = join(alice);
        join(bob);
        assertThat(call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "  "), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "x".repeat(3001)), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> posted = call(alice, HttpMethod.POST, "/network/posts",
            Map.of("text", " Retour d'expérience sur #Kafka à #Lyon, et #kafka encore.\n\nMerci @" + aliceHandle + " ! "), Map.class).getBody();
        assertThat(posted).containsEntry("own", true).containsEntry("tags", List.of("kafka", "lyon"))
            .hasEntrySatisfying("text", text -> assertThat((String) text).startsWith("Retour").endsWith("!"));
        assertThat((Map<String, Object>) posted.get("author")).containsEntry("handle", aliceHandle);
        assertThat(posts(alice, "/network/feed")).singleElement().satisfies(post -> assertThat(post).containsEntry("id", posted.get("id")));

        // Bob reads Alice's posts once he follows her, on her page anyway, and not while he mutes her.
        assertThat(posts(bob, "/network/feed")).isEmpty();
        assertThat(posts(bob, "/network/members/" + aliceHandle + "/posts")).singleElement()
            .satisfies(post -> assertThat(post).containsEntry("own", false));
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        Map<String, Object> second = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Deuxième"), Map.class).getBody();
        assertThat(posts(bob, "/network/feed")).extracting(post -> post.get("id")).containsExactly(second.get("id"), posted.get("id"));
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/mute", null, Map.class);
        assertThat(posts(bob, "/network/feed")).isEmpty();
        call(bob, HttpMethod.DELETE, "/network/members/" + aliceHandle + "/mute", null, Map.class);
        assertThat(call(bob, HttpMethod.GET, "/network/feed?page=1", null, Map.class).getBody()).containsEntry("page", 1)
            .containsEntry("more", false);

        // Only its author deletes a post; a member away hides theirs.
        assertThat(call(bob, HttpMethod.DELETE, "/network/posts/" + second.get("id"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.DELETE, "/network/posts/" + second.get("id"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(posts(bob, "/network/feed")).extracting(post -> post.get("id")).containsExactly(posted.get("id"));
        assertThat(call(alice, HttpMethod.GET, "/users/export", null, String.class).getBody()).contains("\"networkPosts\"")
            .contains("Retour d'expérience").doesNotContain("Deuxième");
        call(alice, HttpMethod.DELETE, "/network/membership", null, String.class);
        assertThat(posts(bob, "/network/feed")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void likesAPostOnceAndListsWhoLikesIt() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        Map<String, Object> posted = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "À aimer"), Map.class).getBody();
        String like = "/network/posts/" + posted.get("id") + "/like";
        assertThat(posted).containsEntry("likes", 0).containsEntry("liked", false);
        assertThat(call(bob, HttpMethod.PUT, like, null, Map.class).getBody()).containsEntry("likes", 1).containsEntry("liked", true);
        assertThat(call(bob, HttpMethod.PUT, like, null, Map.class).getBody()).containsEntry("likes", 1);
        assertThat(call(alice, HttpMethod.PUT, like, null, Map.class).getBody()).containsEntry("likes", 2);
        assertThat(posts(alice, "/network/feed")).singleElement().satisfies(post -> assertThat(post).containsEntry("likes", 2)
            .containsEntry("liked", true));
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/network/posts/" + posted.get("id") + "/likes", null, List.class)
            .getBody()).extracting(card -> card.get("handle")).containsExactly(aliceHandle, bobHandle);
        assertThat(call(bob, HttpMethod.DELETE, like, null, Map.class).getBody()).containsEntry("likes", 1).containsEntry("liked", false);
        assertThat(call(bob, HttpMethod.PUT, "/network/posts/999999/like", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // A member Alice blocks can no longer like her posts nor read who likes them; a deleted post takes its likes along.
        call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/block", null, Map.class);
        assertThat(call(bob, HttpMethod.PUT, like, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.GET, "/network/posts/" + posted.get("id") + "/likes", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.DELETE, "/network/posts/" + posted.get("id"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.PUT, like, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
