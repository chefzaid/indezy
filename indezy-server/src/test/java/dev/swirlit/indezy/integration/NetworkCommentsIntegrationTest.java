package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comments over HTTP: Bob comments Alice's post and replies under a comment (one level only); Alice pins, hides and
 * closes; each deletes their own comment only.
 */
class NetworkCommentsIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void closeTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, false);
    }

    private void join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> comments(Account reader, Object postId) {
        return call(reader, HttpMethod.GET, "/network/posts/" + postId + "/comments", null, List.class).getBody();
    }

    private Map<String, Object> comment(Account account, Object postId, String text, Object parentId) {
        Map<String, Object> body = new HashMap<>(Map.of("text", text));
        if (parentId != null) {
            body.put("parentId", parentId);
        }
        return call(account, HttpMethod.POST, "/network/posts/" + postId + "/comments", body, Map.class).getBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void commentsRepliesPinsHidesAndCloses() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        join(bob);
        Object post = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Une question"), Map.class).getBody().get("id");
        Map<String, Object> first = comment(bob, post, "Première réponse", null);
        Map<String, Object> second = comment(alice, post, "Merci", null);
        Map<String, Object> reply = comment(alice, post, "Je précise", first.get("id"));
        assertThat(call(bob, HttpMethod.POST, "/network/posts/" + post + "/comments", Map.of("text", "Trop loin", "parentId", reply.get("id")),
            String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(bob, HttpMethod.POST, "/network/posts/" + post + "/comments", Map.of("text", " "), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        List<Map<String, Object>> read = comments(bob, post);
        assertThat(read).extracting(comment -> comment.get("id")).containsExactly(first.get("id"), second.get("id"));
        assertThat((List<Map<String, Object>>) read.getFirst().get("replies")).singleElement()
            .satisfies(answer -> assertThat(answer).containsEntry("text", "Je précise").containsEntry("own", false));
        assertThat((List<Map<String, Object>>) call(bob, HttpMethod.GET, "/network/feed", null, Map.class).getBody().get("posts"))
            .isEmpty();
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/network/feed", null, Map.class).getBody().get("posts"))
            .singleElement().satisfies(found -> assertThat(found).containsEntry("comments", 3).containsEntry("commentsClosed", false));

        // Alice pins her comment on top, hides Bob's (then only she sees it, marked hidden), and closes the comments.
        assertThat(call(bob, HttpMethod.PUT, "/network/posts/" + post + "/pinned-comment?commentId=" + second.get("id"), null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        call(alice, HttpMethod.PUT, "/network/posts/" + post + "/pinned-comment?commentId=" + second.get("id"), null, String.class);
        assertThat(comments(bob, post)).first().satisfies(top -> assertThat(top).containsEntry("id", second.get("id"))
            .containsEntry("pinned", true));
        assertThat(call(alice, HttpMethod.PUT, "/network/posts/" + post + "/pinned-comment?commentId=" + reply.get("id"), null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(bob, HttpMethod.PUT, "/network/comments/" + first.get("id") + "/hidden?hidden=true", null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        call(alice, HttpMethod.PUT, "/network/comments/" + first.get("id") + "/hidden?hidden=true", null, String.class);
        assertThat(comments(bob, post)).extracting(comment -> comment.get("id")).containsExactly(second.get("id"));
        assertThat(comments(alice, post)).filteredOn(comment -> comment.get("id").equals(first.get("id"))).singleElement()
            .satisfies(hidden -> assertThat(hidden).containsEntry("hidden", true));
        call(alice, HttpMethod.PUT, "/network/posts/" + post + "/comments-closed?closed=true", null, String.class);
        assertThat(call(bob, HttpMethod.POST, "/network/posts/" + post + "/comments", Map.of("text", "Encore"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // Each deletes their own comment only; a deleted comment takes its replies and its pin along.
        assertThat(call(bob, HttpMethod.DELETE, "/network/comments/" + second.get("id"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        call(alice, HttpMethod.PUT, "/network/comments/" + first.get("id") + "/hidden?hidden=false", null, String.class);
        assertThat(call(bob, HttpMethod.DELETE, "/network/comments/" + first.get("id"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.DELETE, "/network/comments/" + second.get("id"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(comments(alice, post)).isEmpty();
        call(alice, HttpMethod.PUT, "/network/posts/" + post + "/pinned-comment", null, String.class);
        assertThat(call(alice, HttpMethod.DELETE, "/network/posts/" + post, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(bob, HttpMethod.GET, "/network/posts/" + post + "/comments", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
