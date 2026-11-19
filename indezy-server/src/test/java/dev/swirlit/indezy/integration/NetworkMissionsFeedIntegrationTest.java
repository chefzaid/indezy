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
 * The feed's Missions tab over HTTP: Bob reads the missions every member passes on, whether he follows them or not, the
 * newest first, but his own and those of a member he mutes; nothing else than missions.
 */
class NetworkMissionsFeedIntegrationTest extends TwoAccountsIntegrationTest {

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

    private Object mission(Account author, String role) {
        return call(author, HttpMethod.POST, "/network/posts", Map.of("text", "Je ne peux pas la prendre", "kind", "MISSION", "details",
            Map.of("role", role, "rateMin", 600, "rateMax", 700, "city", "Lyon")), Map.class).getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private List<Object> missions(Account reader) {
        Map<String, Object> page = call(reader, HttpMethod.GET, "/network/feed/missions", null, Map.class).getBody();
        return ((List<Map<String, Object>>) page.get("posts")).stream().map(post -> post.get("id")).toList();
    }

    @Test
    void listsTheMissionsPassedOnByEveryMember() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        join(bob);
        Account carol = register("carol");
        String carolHandle = join(carol);
        Object kafka = mission(alice, "Lead Kafka");
        call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Un simple message"), Map.class);
        Object angular = mission(carol, "Lead Angular");
        mission(bob, "Son propre poste");

        // Bob follows nobody: the missions of every member reach him, the newest first, but his own.
        assertThat(missions(bob)).containsExactly(angular, kafka);
        call(bob, HttpMethod.PUT, "/network/members/" + carolHandle + "/mute", null, Map.class);
        assertThat(missions(bob)).containsExactly(kafka);
        assertThat(aliceHandle).isNotBlank();
    }
}
