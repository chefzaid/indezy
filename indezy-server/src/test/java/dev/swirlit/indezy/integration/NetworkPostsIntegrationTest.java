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
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
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

        // A post has its own page, for whoever can read it; a deleted one, like one never written, is not found.
        assertThat(call(bob, HttpMethod.GET, "/network/posts/" + second.get("id"), null, Map.class).getBody())
            .containsEntry("id", second.get("id")).containsEntry("text", "Deuxième").containsEntry("own", false);
        assertThat(call(bob, HttpMethod.GET, "/network/posts/999999999", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Only its author deletes a post; a member away hides theirs.
        assertThat(call(bob, HttpMethod.DELETE, "/network/posts/" + second.get("id"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.DELETE, "/network/posts/" + second.get("id"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(posts(bob, "/network/feed")).extracting(post -> post.get("id")).containsExactly(posted.get("id"));
        assertThat(call(alice, HttpMethod.GET, "/users/export", null, String.class).getBody()).contains("\"networkPosts\"")
            .contains("Retour d'expérience").doesNotContain("Deuxième");
        assertThat(call(bob, HttpMethod.GET, "/network/posts/" + second.get("id"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        call(alice, HttpMethod.DELETE, "/network/membership", null, String.class);
        assertThat(posts(bob, "/network/feed")).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/network/posts/" + posted.get("id"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
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

    @Test
    @SuppressWarnings("unchecked")
    void editsAPostForFifteenMinutesKeepingTheEarlierText() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        join(bob);
        Map<String, Object> posted = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Premier jet #java"), Map.class).getBody();
        String post = "/network/posts/" + posted.get("id");
        assertThat(posted).containsEntry("edited", false);
        assertThat(call(bob, HttpMethod.PUT, post, Map.of("text", "Pas à moi"), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.PUT, post, Map.of("text", "Premier jet #java"), Map.class).getBody()).containsEntry("edited", false);
        assertThat(call(alice, HttpMethod.PUT, post, Map.of("text", "Version revue #kotlin"), Map.class).getBody())
            .containsEntry("edited", true).containsEntry("text", "Version revue #kotlin").containsEntry("tags", List.of("kotlin"));
        assertThat((List<Map<String, Object>>) call(bob, HttpMethod.GET, post + "/versions", null, List.class).getBody()).singleElement()
            .satisfies(version -> assertThat(version).containsEntry("text", "Premier jet #java"));

        jdbcTemplate.update("update network_posts set created_at = ? where id = ?",
            java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(16)), ((Number) posted.get("id")).longValue());
        assertThat(call(alice, HttpMethod.PUT, post, Map.of("text", "Trop tard"), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.DELETE, post, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(bob, HttpMethod.GET, post + "/versions", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void savesPostsToAPrivateListFilteredByTag() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        join(bob);
        Object kafka = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Retour #Kafka"), Map.class).getBody().get("id");
        Object spark = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Atelier #Spark"), Map.class).getBody().get("id");
        assertThat(call(bob, HttpMethod.PUT, "/network/posts/" + kafka + "/bookmark", null, Map.class).getBody())
            .containsEntry("bookmarked", true);
        call(bob, HttpMethod.PUT, "/network/posts/" + spark + "/bookmark", null, Map.class);
        call(bob, HttpMethod.PUT, "/network/posts/" + spark + "/bookmark", null, Map.class);

        Map<String, Object> saved = call(bob, HttpMethod.GET, "/network/bookmarks", null, Map.class).getBody();
        assertThat(saved).containsEntry("tags", List.of("kafka", "spark"));
        assertThat((List<Map<String, Object>>) ((Map<String, Object>) saved.get("page")).get("posts"))
            .extracting(post -> post.get("id")).containsExactly(spark, kafka);
        Map<String, Object> filtered = (Map<String, Object>) call(bob, HttpMethod.GET, "/network/bookmarks?tag=Kafka", null, Map.class)
            .getBody().get("page");
        assertThat((List<Map<String, Object>>) filtered.get("posts")).singleElement()
            .satisfies(post -> assertThat(post).containsEntry("id", kafka).containsEntry("bookmarked", true));
        assertThat((List<Map<String, Object>>) ((Map<String, Object>) call(alice, HttpMethod.GET, "/network/bookmarks", null, Map.class)
            .getBody().get("page")).get("posts")).isEmpty();

        // Taken off, deleted by its author, or blocked: a saved post leaves the list.
        call(bob, HttpMethod.DELETE, "/network/posts/" + spark + "/bookmark", null, Map.class);
        call(alice, HttpMethod.DELETE, "/network/posts/" + kafka, null, String.class);
        Object third = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Troisième"), Map.class).getBody().get("id");
        call(bob, HttpMethod.PUT, "/network/posts/" + third + "/bookmark", null, Map.class);
        assertThat((List<Map<String, Object>>) ((Map<String, Object>) call(bob, HttpMethod.GET, "/network/bookmarks", null, Map.class)
            .getBody().get("page")).get("posts")).extracting(post -> post.get("id")).containsExactly(third);
        String bobHandle = (String) call(bob, HttpMethod.GET, "/network/membership", null, Map.class).getBody().get("handle");
        call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/block", null, Map.class);
        assertThat((List<Map<String, Object>>) ((Map<String, Object>) call(bob, HttpMethod.GET, "/network/bookmarks", null, Map.class)
            .getBody().get("page")).get("posts")).isEmpty();
    }

    @Test
    void refusesAnEleventhPostInAnHour() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        for (int number = 1; number <= 10; number++) {
            assertThat(call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Post " + number), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        }
        org.springframework.http.ResponseEntity<String> refused = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Encore"),
            String.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(refused.getHeaders().getFirst("Retry-After")).isEqualTo("3600");
    }

    @Test
    @SuppressWarnings("unchecked")
    void repostsAPostWithACommentUntilItsAuthorTurnsRepostsOff() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        join(bob);
        Map<String, Object> original = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "À partager"), Map.class).getBody();
        String reposts = "/network/posts/" + original.get("id") + "/reposts";
        Map<String, Object> repost = call(bob, HttpMethod.POST, reposts, Map.of("text", "À lire #kafka"), Map.class).getBody();
        assertThat(repost).containsEntry("text", "À lire #kafka").containsEntry("tags", List.of("kafka"));
        assertThat((Map<String, Object>) repost.get("repostOf")).containsEntry("id", original.get("id")).containsEntry("available", true)
            .containsEntry("text", "À partager");
        // A repost of the repost reposts the original, without a comment.
        Map<String, Object> again = call(alice, HttpMethod.POST, "/network/posts/" + repost.get("id") + "/reposts", Map.of(), Map.class)
            .getBody();
        assertThat(again).containsEntry("text", "");
        assertThat((Map<String, Object>) again.get("repostOf")).containsEntry("id", original.get("id"));
        assertThat(posts(alice, "/network/feed")).filteredOn(post -> post.get("id").equals(original.get("id"))).singleElement()
            .satisfies(post -> assertThat(post).containsEntry("reposts", 2).containsEntry("repostsOff", false));

        assertThat(call(bob, HttpMethod.PUT, "/network/posts/" + original.get("id") + "/reposts-off?off=true", null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.PUT, "/network/posts/" + original.get("id") + "/reposts-off?off=true", null, Map.class).getBody())
            .containsEntry("repostsOff", true);
        assertThat(call(bob, HttpMethod.POST, reposts, Map.of(), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // The original deleted, its reposts stay, saying it is no longer there.
        call(alice, HttpMethod.DELETE, "/network/posts/" + original.get("id"), null, String.class);
        assertThat(posts(bob, "/network/feed")).filteredOn(post -> post.get("id").equals(repost.get("id"))).singleElement()
            .satisfies(post -> assertThat((Map<String, Object>) post.get("repostOf")).containsEntry("available", false));
    }
}
