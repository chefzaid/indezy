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
 * Mutual follows over HTTP: Alice follows Bob, Carol and Erin; Bob and Carol follow Dan, Erin does not; on Dan's page Alice
 * reads that two members she follows follow him, the latest first; once Carol blocks her, one; none on her own page.
 */
class NetworkMutualFollowsIntegrationTest extends TwoAccountsIntegrationTest {

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

    private void follow(Account follower, String handle) {
        call(follower, HttpMethod.PUT, "/network/members/" + handle + "/follow", null, Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tellsWhichFollowedMembersFollowTheMemberToo() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        Account carol = register("carol");
        String carolHandle = join(carol);
        Account dan = register("dan");
        String danHandle = join(dan);
        Account erin = register("erin");
        String erinHandle = join(erin);
        follow(alice, bobHandle);
        follow(alice, carolHandle);
        follow(alice, erinHandle);
        follow(bob, danHandle);
        follow(carol, danHandle);

        Map<String, Object> mutual = call(alice, HttpMethod.GET, "/network/members/" + danHandle + "/mutual", null, Map.class).getBody();
        assertThat(mutual).containsEntry("count", 2);
        assertThat((List<Map<String, Object>>) mutual.get("members")).extracting(card -> card.get("handle"))
            .containsExactly(carolHandle, bobHandle);

        // The search tells it too, for each member found.
        Map<String, Object> found = call(alice, HttpMethod.GET, "/network/search/members?q=" + danHandle, null, Map.class).getBody();
        assertThat((Map<String, Object>) found.get("mutual")).containsEntry(danHandle, 2);
        assertThat((Map<String, Object>) call(alice, HttpMethod.GET, "/network/members", null, Map.class).getBody().get("mutual")).isEmpty();

        call(carol, HttpMethod.PUT, "/network/members/" + aliceHandle + "/block", null, Map.class);
        assertThat(call(alice, HttpMethod.GET, "/network/members/" + danHandle + "/mutual", null, Map.class).getBody())
            .containsEntry("count", 1);
        assertThat(call(alice, HttpMethod.GET, "/network/members/" + aliceHandle + "/mutual", null, Map.class).getBody())
            .containsEntry("count", 0);
    }
}
