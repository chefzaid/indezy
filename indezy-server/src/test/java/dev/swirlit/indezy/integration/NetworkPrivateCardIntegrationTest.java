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
 * Private cards over HTTP: Alice turns hers private; Bob asks to follow and reads her posts once she approves, Carol is
 * declined, and Dave's request is approved when Alice turns her card public again.
 */
class NetworkPrivateCardIntegrationTest extends TwoAccountsIntegrationTest {

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
    private List<Object> postsOf(Account reader, String handle) {
        return ((List<Map<String, Object>>) call(reader, HttpMethod.GET, "/network/members/" + handle + "/posts", null, Map.class).getBody()
            .get("posts")).stream().map(post -> post.get("id")).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> handles(Account account, String path) {
        return ((List<Map<String, Object>>) call(account, HttpMethod.GET, path, null, List.class).getBody()).stream()
            .map(card -> (String) card.get("handle")).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Object> noticeTypes(Account account) {
        return call(account, HttpMethod.GET, "/network/notifications", null, List.class).getBody().stream()
            .map(group -> ((Map<String, Object>) group).get("type")).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyApprovedFollowersReadAPrivateMembersPosts() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        Account carol = register("carol");
        String carolHandle = join(carol);
        Account dave = register("dave");
        join(dave);
        String tag = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Object post = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Entre nous #" + tag), Map.class).getBody().get("id");
        call(bob, HttpMethod.PUT, "/network/tags/" + tag + "/follow", null, List.class);
        assertThat(postsOf(bob, aliceHandle)).containsExactly(post);

        assertThat(call(alice, HttpMethod.PUT, "/network/membership/private?private=true", null, Map.class).getBody())
            .containsEntry("privateCard", true);
        assertThat(call(bob, HttpMethod.GET, "/network/members/" + aliceHandle + "/page", null, Map.class).getBody())
            .containsEntry("privateCard", true).containsEntry("followed", false).containsEntry("requested", false);
        assertThat(postsOf(bob, aliceHandle)).isEmpty();
        assertThat(postsOf(alice, aliceHandle)).containsExactly(post);
        assertThat((List<Object>) call(bob, HttpMethod.GET, "/network/feed", null, Map.class).getBody().get("posts")).isEmpty();
        assertThat((List<Object>) ((Map<String, Object>) call(bob, HttpMethod.GET, "/network/tags/" + tag, null, Map.class).getBody()
            .get("posts")).get("posts")).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/network/posts/" + post + "/comments", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // Bob asks; Alice is told and approves; Bob is told and reads her posts.
        assertThat(call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class).getBody())
            .containsEntry("requested", true).containsEntry("followed", false);
        assertThat(noticeTypes(alice)).contains("FOLLOW_REQUEST");
        call(carol, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        assertThat(handles(alice, "/network/follow-requests")).containsExactly(bobHandle, carolHandle);
        assertThat(handles(alice, "/network/members/" + aliceHandle + "/followers")).isEmpty();
        assertThat(call(alice, HttpMethod.PUT, "/network/follow-requests/" + bobHandle, null, List.class).getBody()).hasSize(1);
        assertThat(call(bob, HttpMethod.GET, "/network/members/" + aliceHandle + "/page", null, Map.class).getBody())
            .containsEntry("followed", true).containsEntry("requested", false);
        assertThat(noticeTypes(bob)).contains("FOLLOW_ACCEPTED");
        assertThat(postsOf(bob, aliceHandle)).containsExactly(post);
        assertThat(call(bob, HttpMethod.GET, "/network/posts/" + post + "/comments", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);

        // Carol is declined, untold; a request withdrawn by unfollowing is gone.
        call(alice, HttpMethod.DELETE, "/network/follow-requests/" + carolHandle, null, List.class);
        assertThat(handles(alice, "/network/follow-requests")).isEmpty();
        assertThat(postsOf(carol, aliceHandle)).isEmpty();
        assertThat(noticeTypes(carol)).doesNotContain("FOLLOW_ACCEPTED");
        assertThat(call(alice, HttpMethod.PUT, "/network/follow-requests/" + carolHandle, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        call(carol, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        call(carol, HttpMethod.DELETE, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        assertThat(handles(alice, "/network/follow-requests")).isEmpty();

        // Public again: Dave's waiting request is approved.
        call(dave, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        assertThat(call(alice, HttpMethod.PUT, "/network/membership/private?private=false", null, Map.class).getBody())
            .containsEntry("privateCard", false);
        assertThat(handles(alice, "/network/follow-requests")).isEmpty();
        assertThat(call(dave, HttpMethod.GET, "/network/members/" + aliceHandle + "/page", null, Map.class).getBody())
            .containsEntry("followed", true);
        assertThat(postsOf(carol, aliceHandle)).containsExactly(post);
    }
}
