package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Polls over HTTP: Alice asks, Bob sees the votes once he voted and may change his choice until the poll closes. */
class NetworkPollIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @Autowired
    private NetworkPostRepository posts;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    private HttpStatus post(Map<String, Object> body) {
        return HttpStatus.valueOf(call(alice, HttpMethod.POST, "/network/posts", body, String.class).getStatusCode().value());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pollOf(Account reader, String handle, Object postId) {
        List<Map<String, Object>> found = (List<Map<String, Object>>) call(reader, HttpMethod.GET, "/network/members/" + handle + "/posts",
            null, Map.class).getBody().get("posts");
        return (Map<String, Object>) found.stream().filter(post -> post.get("id").equals(postId)).findFirst().orElseThrow().get("poll");
    }

    @Test
    @SuppressWarnings("unchecked")
    void votesOnceChangingTheChoiceUntilThePollCloses() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String handle = join(alice);
        join(bob);
        assertThat(post(Map.of("text", "Un seul choix", "kind", "POLL", "poll", Map.of("choices", List.of("Oui"), "days", 3))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(Map.of("text", "Trop long", "kind", "POLL", "poll", Map.of("choices", List.of("Oui", "Non"), "days", 8))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(Map.of("text", "Deux fois", "kind", "POLL", "poll", Map.of("choices", List.of("Oui", " Oui "), "days", 3))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(Map.of("text", "Sans choix", "kind", "POLL"))).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> created = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Quel IDE ?", "kind", "POLL",
            "poll", Map.of("choices", List.of("IntelliJ", "VS Code", "Vim"), "days", 3)), Map.class).getBody();
        Object id = created.get("id");
        assertThat((Map<String, Object>) created.get("poll")).containsEntry("closed", false).containsEntry("total", 0);
        Map<String, Object> unseen = pollOf(bob, handle, id);
        assertThat(unseen).containsEntry("total", null).containsEntry("myChoice", null);
        assertThat((List<Map<String, Object>>) unseen.get("choices")).extracting(choice -> choice.get("votes")).containsOnlyNulls();

        Map<String, Object> voted = call(bob, HttpMethod.PUT, "/network/posts/" + id + "/vote?choice=1", null, Map.class).getBody();
        assertThat((Map<String, Object>) voted.get("poll")).containsEntry("myChoice", 1).containsEntry("total", 1);
        call(bob, HttpMethod.PUT, "/network/posts/" + id + "/vote?choice=2", null, Map.class);
        Map<String, Object> changed = pollOf(alice, handle, id);
        assertThat((List<Map<String, Object>>) changed.get("choices")).extracting(choice -> choice.get("votes")).containsExactly(0, 0, 1);
        assertThat(call(bob, HttpMethod.PUT, "/network/posts/" + id + "/vote?choice=3", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        Object plain = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Bonjour"), Map.class).getBody().get("id");
        assertThat(call(bob, HttpMethod.PUT, "/network/posts/" + plain + "/vote?choice=0", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // Closed: the votes are shown to all, and no vote is taken.
        posts.findById(((Number) id).longValue()).ifPresent(poll -> {
            poll.setPollEndsAt(Instant.now().minusSeconds(60));
            posts.save(poll);
        });
        assertThat(call(bob, HttpMethod.PUT, "/network/posts/" + id + "/vote?choice=0", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        Account carol = register("carol");
        join(carol);
        assertThat(pollOf(carol, handle, id)).containsEntry("closed", true).containsEntry("total", 1);
        call(alice, HttpMethod.DELETE, "/network/posts/" + id, null, String.class);
    }
}
