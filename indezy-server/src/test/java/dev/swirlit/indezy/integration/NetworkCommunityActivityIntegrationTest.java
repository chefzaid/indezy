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
 * A community's activity over HTTP: its posts reach its members' home feeds, named on their cards; its members are told of
 * a post, a member named moderator too; a private community is joined on request, its moderators told and answering, its
 * posts read by its members alone, anywhere.
 */
class NetworkCommunityActivityIntegrationTest extends TwoAccountsIntegrationTest {

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
    private List<Map<String, Object>> feed(Account reader) {
        return (List<Map<String, Object>>) call(reader, HttpMethod.GET, "/network/feed", null, Map.class).getBody().get("posts");
    }

    @SuppressWarnings("unchecked")
    private List<String> notices(Account reader) {
        return ((List<Map<String, Object>>) call(reader, HttpMethod.GET, "/network/notifications", null, List.class).getBody()).stream()
            .map(group -> group.get("type") + ":" + (group.get("community") == null ? null : ((Map<String, Object>) group.get("community")).get("name")))
            .toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void tellsAndShowsACommunitysActivity() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        String bobHandle = join(bob);
        Account carol = register("carol");
        String carolHandle = join(carol);
        String name = "Kafka " + UUID.randomUUID().toString().substring(0, 6);
        String slug = (String) call(alice, HttpMethod.POST, "/network/communities", Map.of("name", name), Map.class).getBody().get("slug");
        String path = "/network/communities/" + slug;
        call(bob, HttpMethod.PUT, path + "/membership", null, Map.class);

        // A post in it reaches Bob's home feed though he follows no one, named on its card; Bob is told of it.
        Map<String, Object> posted = call(alice, HttpMethod.POST, path + "/posts", Map.of("text", "Bienvenue"), Map.class).getBody();
        assertThat((Map<String, Object>) posted.get("community")).containsEntry("slug", slug).containsEntry("name", name);
        assertThat(feed(bob)).anySatisfy(post -> {
            assertThat(post).containsEntry("id", posted.get("id"));
            assertThat((Map<String, Object>) post.get("community")).containsEntry("name", name);
        });
        assertThat(feed(carol)).noneSatisfy(post -> assertThat(post).containsEntry("id", posted.get("id")));
        assertThat(notices(bob)).contains("COMMUNITY_POST:" + name);
        call(alice, HttpMethod.PUT, path + "/moderators/" + bobHandle, null, List.class);
        assertThat(notices(bob)).contains("COMMUNITY_MODERATOR:" + name);

        // Made private, it is joined on request: its moderators are told, and answer; Carol is let in, and told.
        call(alice, HttpMethod.PUT, path, Map.of("name", name, "privateCommunity", true), Map.class);
        Object secret = call(alice, HttpMethod.POST, path + "/posts", Map.of("text", "Entre nous"), Map.class).getBody().get("id");
        assertThat(call(carol, HttpMethod.GET, "/network/posts/" + secret, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((List<Object>) call(carol, HttpMethod.GET, path + "/posts", null, Map.class).getBody().get("posts")).isEmpty();
        Map<String, Object> asked = call(carol, HttpMethod.PUT, path + "/membership", null, Map.class).getBody();
        assertThat((Map<String, Object>) asked.get("community")).containsEntry("joined", false).containsEntry("requested", true)
            .containsEntry("privateCommunity", true);
        assertThat(call(carol, HttpMethod.POST, path + "/posts", Map.of("text", "Moi aussi"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(notices(alice)).contains("COMMUNITY_REQUEST:" + name);
        assertThat(notices(bob)).contains("COMMUNITY_REQUEST:" + name);
        assertThat(call(carol, HttpMethod.GET, path + "/requests", null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        List<Map<String, Object>> waiting = call(bob, HttpMethod.GET, path + "/requests", null, List.class).getBody();
        assertThat(waiting).extracting(card -> card.get("handle")).containsExactly(carolHandle);
        assertThat(call(bob, HttpMethod.PUT, path + "/requests/" + carolHandle, null, List.class).getBody()).isEmpty();
        assertThat(notices(carol)).contains("COMMUNITY_ACCEPTED:" + name);
        assertThat(call(carol, HttpMethod.GET, "/network/posts/" + secret, null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Object>) call(carol, HttpMethod.GET, path + "/posts", null, Map.class).getBody().get("posts")).isNotEmpty();

        // A declined request leaves the member out, untold.
        Account dan = register("dan");
        String danHandle = join(dan);
        call(dan, HttpMethod.PUT, path + "/membership", null, Map.class);
        call(alice, HttpMethod.DELETE, path + "/requests/" + danHandle, null, List.class);
        assertThat((Map<String, Object>) call(dan, HttpMethod.GET, path, null, Map.class).getBody().get("community"))
            .containsEntry("joined", false).containsEntry("requested", false);
        assertThat(notices(dan)).noneMatch(notice -> notice.startsWith("COMMUNITY_ACCEPTED"));
    }
}
