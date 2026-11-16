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
 * Muting and blocking over HTTP: Alice mutes Bob and still follows him; then blocks him, which ends both follows, keeps
 * him from following her and hides her page from him, until she unblocks him.
 */
class NetworkRestrictionsIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String join(Account account, String prefix) {
        String handle = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    @Test
    @SuppressWarnings("unchecked")
    void mutesAndBlocksAMember() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice, "alice");
        String bobHandle = join(bob, "bob");
        call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/follow", null, Map.class);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);

        assertThat(call(alice, HttpMethod.PUT, "/network/members/" + aliceHandle + "/mute", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/mute", null, Map.class).getBody())
            .containsEntry("muted", true).containsEntry("followed", true).containsEntry("blocked", false);
        Map<String, Object> restrictions = call(alice, HttpMethod.GET, "/network/restrictions", null, Map.class).getBody();
        assertThat((List<Map<String, Object>>) restrictions.get("muted")).singleElement()
            .satisfies(card -> assertThat(card).containsEntry("handle", bobHandle));
        assertThat(call(alice, HttpMethod.DELETE, "/network/members/" + bobHandle + "/mute", null, Map.class).getBody())
            .containsEntry("muted", false);

        Map<String, Object> blocked = call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/block", null, Map.class).getBody();
        assertThat(blocked).containsEntry("blocked", true).containsEntry("followed", false).containsEntry("followsYou", false);
        assertThat(call(bob, HttpMethod.GET, "/network/members/" + aliceHandle + "/page", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/follow", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/network/restrictions", null, Map.class).getBody().get("blocked"))
            .singleElement().satisfies(card -> assertThat(card).containsEntry("handle", bobHandle));
        // Neither appears in lists the other reads.
        String carol = join(register("carol"), "carol");
        call(bob, HttpMethod.PUT, "/network/members/" + carol + "/follow", null, Map.class);
        assertThat(call(alice, HttpMethod.GET, "/network/members/" + carol + "/followers", null, List.class).getBody()).isEmpty();

        assertThat(call(alice, HttpMethod.DELETE, "/network/members/" + bobHandle + "/block", null, Map.class).getBody())
            .containsEntry("blocked", false).containsEntry("followed", false);
        assertThat(call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class).getBody())
            .containsEntry("followed", true);
    }
}
