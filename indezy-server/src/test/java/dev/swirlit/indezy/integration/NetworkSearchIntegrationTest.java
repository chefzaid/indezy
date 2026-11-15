package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The global search over HTTP finds Bob, a member, by his handle and his post by its words, for Alice once she joined,
 * while the network is on and neither blocks the other.
 */
class NetworkSearchIntegrationTest extends TwoAccountsIntegrationTest {

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
    private List<Map<String, Object>> found(Account account, String kind, String query) {
        return (List<Map<String, Object>>) call(account, HttpMethod.GET, "/search?q=" + query, null, Map.class).getBody().get(kind);
    }

    @Test
    void findsMembersAndPostsForAMemberOnly() {
        String word = "kafka" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        assertThat(found(alice, "members", word)).isEmpty();
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String bobHandle = join(bob);
        Object post = call(bob, HttpMethod.POST, "/network/posts", Map.of("text", "Un retour sur " + word + " en production"), Map.class)
            .getBody().get("id");
        assertThat(found(alice, "posts", word)).isEmpty();

        join(alice);
        assertThat(found(alice, "members", bobHandle)).singleElement().satisfies(hit -> assertThat(hit)
            .containsEntry("handle", bobHandle).containsEntry("subtitle", "@" + bobHandle).containsKey("id"));
        assertThat(found(alice, "posts", word.toUpperCase())).singleElement().satisfies(hit -> assertThat(hit)
            .containsEntry("id", post).containsEntry("handle", bobHandle).containsEntry("excerpt", "Un retour sur " + word + " en production"));
        assertThat(found(bob, "members", bobHandle)).isEmpty();
        // An API token reads the workspace only, never the network.
        String secret = (String) call(alice, HttpMethod.POST, "/users/api-tokens", Map.of("name", "Script", "expiresInDays", 90), Map.class)
            .getBody().get("secret");
        ResponseEntity<String> byToken = call(new Account(secret, alice.freelanceId()), HttpMethod.GET, "/search?q=" + word, null,
            String.class);
        assertThat(byToken.getStatusCode()).as(byToken.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(byToken.getBody()).contains("\"posts\":[]");
        assertThat(call(new Account(secret, alice.freelanceId()), HttpMethod.GET, "/network/feed", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/block", null, Map.class);
        assertThat(found(alice, "members", bobHandle)).isEmpty();
        assertThat(found(alice, "posts", word)).isEmpty();
        assertThat(found(bob, "posts", word)).singleElement();
    }
}
