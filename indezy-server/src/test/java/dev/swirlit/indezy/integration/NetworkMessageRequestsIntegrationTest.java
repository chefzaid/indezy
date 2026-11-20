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
 * Message requests over HTTP: Alice, whom Bob does not follow nor she him, writes him one message as a request, then
 * waits; Bob reads it among his requests and accepts it, both writing since. Carol's request he declines, she untold; once
 * he takes no requests, Dan cannot write to him.
 */
class NetworkMessageRequestsIntegrationTest extends TwoAccountsIntegrationTest {

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

    private Map<String, Object> open(Account account, String handle) {
        return call(account, HttpMethod.POST, "/network/conversations", Map.of("handle", handle), Map.class).getBody();
    }

    private ResponseEntity<Map> send(Account account, Object conversation, String text) {
        return call(account, HttpMethod.POST, "/network/conversations/" + conversation + "/messages", Map.of("text", text), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Account account, String path) {
        return call(account, HttpMethod.GET, "/network/conversations" + path, null, List.class).getBody();
    }

    private Map<String, Object> unread(Account account) {
        return call(account, HttpMethod.GET, "/network/conversations/unread", null, Map.class).getBody();
    }

    @Test
    void writesToAMemberNeitherFollowsAsARequestTheyAnswer() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        assertThat(call(alice, HttpMethod.GET, "/network/members/" + bobHandle + "/page", null, Map.class).getBody())
            .containsEntry("messageable", true).containsEntry("followed", false).containsEntry("followsYou", false);

        // Alice's request: one message, then it waits; Bob's conversations stay as they were.
        Map<String, Object> opened = open(alice, bobHandle);
        Object conversation = opened.get("id");
        assertThat(opened).containsEntry("closed", null);
        assertThat(list(bob, "")).isEmpty();
        assertThat(send(alice, conversation, "Bonjour Bob, une mission Kafka").getStatusCode().is2xxSuccessful()).isTrue();
        ResponseEntity<Map> second = send(alice, conversation, "Vous êtes là ?");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).containsEntry("message", "Your message request waits for this member's answer");
        assertThat(list(alice, "")).singleElement().satisfies(found -> assertThat(found).containsEntry("closed", "REQUEST_SENT"));

        // Bob finds it among his requests, reads it, and answers before writing.
        assertThat(unread(bob)).containsEntry("conversations", 0).containsEntry("requests", 1);
        assertThat(list(bob, "")).isEmpty();
        assertThat(list(bob, "/requests")).singleElement().satisfies(found -> assertThat(found).containsEntry("id", conversation)
            .containsEntry("closed", "REQUEST_RECEIVED"));
        assertThat(call(bob, HttpMethod.GET, "/network/conversations/" + conversation, null, Map.class).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(send(bob, conversation, "Bonjour").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.PUT, "/network/conversations/" + conversation + "/request", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> accepted = call(bob, HttpMethod.PUT, "/network/conversations/" + conversation + "/request", null, Map.class).getBody();
        assertThat(accepted).containsEntry("id", conversation).containsEntry("closed", null);
        assertThat(list(bob, "/requests")).isEmpty();
        assertThat(list(bob, "")).singleElement().satisfies(found -> assertThat(found).containsEntry("id", conversation));
        assertThat(unread(bob)).containsEntry("requests", 0);
        assertThat(send(bob, conversation, "Avec plaisir").getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(send(alice, conversation, "Je vous envoie la fiche").getStatusCode().is2xxSuccessful()).isTrue();

        // Carol's request Bob declines: gone for him, still waiting as Carol sees it, and she writes no more.
        Account carol = register("carol");
        join(carol);
        Object carols = open(carol, bobHandle).get("id");
        send(carol, carols, "Bonjour");
        assertThat(call(bob, HttpMethod.DELETE, "/network/conversations/" + carols + "/request", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(list(bob, "/requests")).isEmpty();
        assertThat(list(bob, "")).extracting(found -> found.get("id")).containsExactly(conversation);
        assertThat(list(carol, "")).singleElement().satisfies(found -> assertThat(found).containsEntry("closed", "REQUEST_SENT"));
        assertThat(send(carol, carols, "Relance").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(bob, HttpMethod.DELETE, "/network/conversations/" + carols + "/request", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // Bob takes no more requests: Dan cannot write to him, and his page says so.
        call(bob, HttpMethod.PUT, "/network/messages/settings", Map.of("requests", false), Map.class);
        Account dan = register("dan");
        join(dan);
        assertThat(call(dan, HttpMethod.GET, "/network/members/" + bobHandle + "/page", null, Map.class).getBody()).containsEntry("messageable", false);
        assertThat(call(dan, HttpMethod.POST, "/network/conversations", Map.of("handle", bobHandle), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(bob, HttpMethod.GET, "/network/members/" + aliceHandle + "/page", null, Map.class).getBody()).containsEntry("messageable", true);
    }
}
