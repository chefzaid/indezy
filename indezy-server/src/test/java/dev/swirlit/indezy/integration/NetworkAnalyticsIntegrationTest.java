package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Posts' analytics over HTTP: Bob and Carol are shown Alice's post (Bob twice), Alice reads its impressions, members reached
 * and saves, never Bob; her own readings count nothing; her activity counts her posts, reach, followers and missions taken.
 */
class NetworkAnalyticsIntegrationTest extends TwoAccountsIntegrationTest {

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

    private void shown(Account viewer, Object... ids) {
        assertThat(call(viewer, HttpMethod.POST, "/network/posts/impressions", Map.of("ids", List.of(ids)), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void countsWhoWasShownAPostForItsAuthor() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        join(bob);
        Account carol = register("carol");
        join(carol);
        Object post = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Retour sur Kafka"), Map.class).getBody().get("id");
        Object mission = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Je ne peux pas la prendre", "kind", "MISSION",
            "details", Map.of("role", "Lead Java")), Map.class).getBody().get("id");

        shown(bob, post, mission);
        shown(bob, post, 999_999_999L);
        shown(carol, post);
        shown(alice, post);
        call(bob, HttpMethod.PUT, "/network/posts/" + post + "/bookmark", null, Map.class);

        Map<String, Object> stats = call(alice, HttpMethod.GET, "/network/posts/" + post + "/stats", null, Map.class).getBody();
        assertThat(stats).containsEntry("impressions", 3).containsEntry("reached", 2).containsEntry("saves", 1);
        assertThat(call(bob, HttpMethod.GET, "/network/posts/" + post + "/stats", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.POST, "/network/posts/impressions", Map.of("ids", Collections.nCopies(51, post)), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        call(carol, HttpMethod.POST, "/network/posts/" + mission + "/pipeline?named=false", null, Map.class);
        Map<String, Object> activity = call(alice, HttpMethod.GET, "/network/activity", null, Map.class).getBody();
        assertThat(activity).containsEntry("postsThisWeek", 2).containsEntry("reachedThisWeek", 3).containsEntry("followersGained", 1)
            .containsEntry("missionsPassedOn", 1).containsEntry("missionsTaken", 1);

        // A deleted post takes its impressions with it.
        call(alice, HttpMethod.DELETE, "/network/posts/" + post, null, String.class);
        assertThat(call(alice, HttpMethod.GET, "/network/activity", null, Map.class).getBody()).containsEntry("reachedThisWeek", 1);
    }
}
