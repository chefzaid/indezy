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
 * Tags over HTTP: a tag's page shows its posts and the members whose cards show the skill; Bob follows the tag, and
 * Alice's posts naming it reach his feed though he does not follow her, until he mutes her.
 */
class NetworkTagsIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void closeTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, false);
    }

    private String join(Account account, List<String> skills) {
        Map<String, Object> profile = call(account, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        profile.put("skills", skills);
        call(account, HttpMethod.PUT, "/users/profile", profile, Map.class);
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    @SuppressWarnings("unchecked")
    private List<Object> feed(Account reader) {
        return ((List<Map<String, Object>>) call(reader, HttpMethod.GET, "/network/feed", null, Map.class).getBody().get("posts")).stream()
            .map(post -> post.get("id")).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void followsATagAndReadsItsPage() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String tag = "tag" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String aliceHandle = join(alice, List.of(tag));
        join(bob, List.of("Go"));
        Object tagged = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Retour sur #" + tag), Map.class).getBody().get("id");
        Object other = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Sans tag"), Map.class).getBody().get("id");

        Map<String, Object> page = call(bob, HttpMethod.GET, "/network/tags/" + tag.toUpperCase(), null, Map.class).getBody();
        assertThat(page).containsEntry("tag", tag).containsEntry("followed", false);
        assertThat((List<Map<String, Object>>) ((Map<String, Object>) page.get("posts")).get("posts")).extracting(post -> post.get("id"))
            .containsExactly(tagged);
        assertThat((List<Map<String, Object>>) page.get("members")).extracting(card -> card.get("handle")).containsExactly(aliceHandle);
        assertThat(call(bob, HttpMethod.GET, "/network/tags/not a tag", null, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(feed(bob)).isEmpty();
        assertThat(call(bob, HttpMethod.PUT, "/network/tags/" + tag + "/follow", null, List.class).getBody()).containsExactly(tag);
        assertThat(call(bob, HttpMethod.GET, "/network/tags", null, List.class).getBody()).containsExactly(tag);
        assertThat(feed(bob)).containsExactly(tagged);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        assertThat(feed(bob)).containsExactly(other, tagged);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/mute", null, Map.class);
        assertThat(feed(bob)).isEmpty();
        assertThat((List<Map<String, Object>>) ((Map<String, Object>) call(bob, HttpMethod.GET, "/network/tags/" + tag, null, Map.class)
            .getBody().get("posts")).get("posts")).isEmpty();
        call(bob, HttpMethod.DELETE, "/network/members/" + aliceHandle + "/mute", null, Map.class);
        call(bob, HttpMethod.DELETE, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        assertThat(call(bob, HttpMethod.DELETE, "/network/tags/" + tag + "/follow", null, List.class).getBody()).isEmpty();
        assertThat(feed(bob)).isEmpty();
    }
}
