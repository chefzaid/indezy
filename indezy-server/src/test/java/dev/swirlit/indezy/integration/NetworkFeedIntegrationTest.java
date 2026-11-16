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
 * The feed's helpers over HTTP: how many posts reached Bob's feed since his latest, the trending tags, and the tags in use
 * to complete a #tag.
 */
class NetworkFeedIntegrationTest extends TwoAccountsIntegrationTest {

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

    private Object newer(Account reader, String since) {
        return call(reader, HttpMethod.GET, "/network/feed/newer?since=" + since, null, Map.class).getBody().get("count");
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsNewerPostsTrendsAndSuggestsTags() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String run = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String aliceHandle = join(alice);
        join(bob);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        Map<String, Object> mine = call(bob, HttpMethod.POST, "/network/posts", Map.of("text", "Le mien"), Map.class).getBody();
        String since = (String) mine.get("createdAt");

        // Alice's two posts after Bob's latest reach his feed; his own never counts.
        assertThat(newer(bob, since)).isEqualTo(0);
        call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Sur #ztag" + run + " et #ztag" + run + "_b"), Map.class);
        call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Encore #ztag" + run), Map.class);
        call(bob, HttpMethod.POST, "/network/posts", Map.of("text", "Moi encore"), Map.class);
        assertThat(newer(bob, since)).isEqualTo(2);
        assertThat(call(bob, HttpMethod.GET, "/network/feed/newer?since=hier", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        // The tags in use complete what was typed, the most used first; an underscore is no wildcard.
        assertThat(call(bob, HttpMethod.GET, "/network/tags/suggest?q=ZTAG" + run, null, List.class).getBody())
            .containsExactly("ztag" + run, "ztag" + run + "_b");
        assertThat(call(bob, HttpMethod.GET, "/network/tags/suggest?q=ztag" + run + "_", null, List.class).getBody())
            .containsExactly("ztag" + run + "_b");

        // Trending: the most named first, eight at most.
        List<Map<String, Object>> trending = call(bob, HttpMethod.GET, "/network/tags/trending", null, List.class).getBody();
        assertThat(trending).isNotEmpty().hasSizeLessThanOrEqualTo(8);
        List<Integer> counts = trending.stream().map(tag -> ((Number) tag.get("posts")).intValue()).toList();
        assertThat(counts).isSortedAccordingTo((first, second) -> second - first);
        assertThat(call(alice, HttpMethod.GET, "/network/tags/trending", null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
