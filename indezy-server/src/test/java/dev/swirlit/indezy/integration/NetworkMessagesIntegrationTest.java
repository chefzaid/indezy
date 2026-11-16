package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conversations over HTTP: Alice writes to Bob once he follows her; Bob reads, replies and reacts; Alice edits and
 * deletes; muting, archiving, the unread count; and the conversation closing when Bob stops following her or blocks her.
 */
class NetworkMessagesIntegrationTest extends TwoAccountsIntegrationTest {

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

    private Object unread(Account account) {
        return call(account, HttpMethod.GET, "/network/conversations/unread", null, Map.class).getBody().get("conversations");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Account account, String query) {
        return call(account, HttpMethod.GET, "/network/conversations" + query, null, List.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(Account account, Object conversation) {
        return (List<Map<String, Object>>) call(account, HttpMethod.GET, "/network/conversations/" + conversation, null, Map.class)
            .getBody().get("messages");
    }

    private Map<String, Object> send(Account account, Object conversation, Map<String, Object> body) {
        return call(account, HttpMethod.POST, "/network/conversations/" + conversation + "/messages", body, Map.class).getBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesOnlyToAFollowerAndKeepsThePastReadable() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        assertThat(unread(alice)).isEqualTo(0);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        Account carol = register("carol");
        join(carol);

        // Open only towards a member who follows the sender, once.
        ResponseEntity<Map> refused = call(alice, HttpMethod.POST, "/network/conversations", Map.of("handle", bobHandle), Map.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody()).containsEntry("message", "Messages are open only when the other member follows you");
        assertThat(call(alice, HttpMethod.POST, "/network/conversations", Map.of("handle", aliceHandle), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        Map<String, Object> opened = call(alice, HttpMethod.POST, "/network/conversations", Map.of("handle", bobHandle), Map.class).getBody();
        Object conversation = opened.get("id");
        assertThat((Map<String, Object>) opened.get("with")).containsEntry("handle", bobHandle);
        assertThat(opened).containsEntry("closed", null).containsEntry("unread", 0);
        assertThat(call(alice, HttpMethod.POST, "/network/conversations", Map.of("handle", bobHandle), Map.class).getBody())
            .containsEntry("id", conversation);
        assertThat(list(bob, "")).isEmpty();

        // Alice writes; Bob sees it unread, reads it, replies quoting it and reacts.
        Map<String, Object> first = send(alice, conversation, Map.of("text", "  Bonjour Bob, une mission Kafka ?  "));
        assertThat(first).containsEntry("mine", true).containsEntry("text", "Bonjour Bob, une mission Kafka ?").containsEntry("editable", true);
        assertThat(unread(bob)).isEqualTo(1);
        assertThat(list(bob, "")).singleElement().satisfies(found -> {
            assertThat(found).containsEntry("unread", 1).containsEntry("closed", "NOT_FOLLOWED");
            assertThat((Map<String, Object>) found.get("last")).containsEntry("mine", false).containsEntry("text", "Bonjour Bob, une mission Kafka ?");
        });
        assertThat(messages(bob, conversation)).singleElement().satisfies(message -> assertThat(message).containsEntry("mine", false)
            .containsEntry("editable", false));
        call(bob, HttpMethod.PUT, "/network/conversations/" + conversation + "/read", null, Map.class);
        assertThat(unread(bob)).isEqualTo(0);
        assertThat(call(bob, HttpMethod.POST, "/network/conversations/" + conversation + "/messages", Map.of("text", "Oui"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/follow", null, Map.class);
        Map<String, Object> reply = send(bob, conversation, Map.of("text", "Avec plaisir", "replyTo", first.get("id")));
        assertThat((Map<String, Object>) reply.get("replyTo")).containsEntry("mine", false).containsEntry("excerpt", "Bonjour Bob, une mission Kafka ?");
        assertThat(call(bob, HttpMethod.POST, "/network/conversations/" + conversation + "/messages", Map.of("text", "x", "replyTo", 999999999),
            String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> reacted = call(bob, HttpMethod.PUT, "/network/messages/" + first.get("id") + "/reaction?reaction=BRAVO", null,
            Map.class).getBody();
        assertThat(reacted).containsEntry("myReaction", "BRAVO").containsEntry("reactions", List.of(Map.of("reaction", "BRAVO", "count", 1)));
        assertThat(call(bob, HttpMethod.DELETE, "/network/messages/" + first.get("id") + "/reaction", null, Map.class).getBody())
            .containsEntry("myReaction", null);
        call(bob, HttpMethod.PUT, "/network/messages/" + first.get("id") + "/reaction", null, Map.class);

        // Alice edits, then deletes her message for both; Bob cannot change it, Carol cannot read any of it.
        assertThat(call(bob, HttpMethod.PUT, "/network/messages/" + first.get("id"), Map.of("text", "Non"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.PUT, "/network/messages/" + first.get("id"), Map.of("text", "Bonjour Bob, une mission Java ?"),
            Map.class).getBody()).containsEntry("edited", true).containsEntry("text", "Bonjour Bob, une mission Java ?");
        Map<String, Object> deleted = call(alice, HttpMethod.DELETE, "/network/messages/" + first.get("id"), null, Map.class).getBody();
        assertThat(deleted).containsEntry("deleted", true).containsEntry("text", "").containsEntry("reactions", List.of());
        assertThat(call(bob, HttpMethod.PUT, "/network/messages/" + first.get("id") + "/reaction", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(messages(bob, conversation)).extracting(message -> message.get("deleted")).containsExactly(true, false);
        assertThat(call(carol, HttpMethod.GET, "/network/conversations/" + conversation, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(carol, HttpMethod.PUT, "/network/messages/" + reply.get("id") + "/reaction", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // Muted, the conversation counts in no unread total; archived, it leaves the list until a new message comes.
        call(alice, HttpMethod.PUT, "/network/conversations/" + conversation + "/settings", Map.of("muted", true), Map.class);
        send(bob, conversation, Map.of("text", "Tu es là ?"));
        assertThat(unread(alice)).isEqualTo(0);
        Map<String, Object> archive = new HashMap<>();
        archive.put("archived", true);
        call(alice, HttpMethod.PUT, "/network/conversations/" + conversation + "/settings", archive, Map.class);
        assertThat(list(alice, "")).isEmpty();
        assertThat(list(alice, "?archived=true")).singleElement().satisfies(found -> assertThat(found).containsEntry("archived", true)
            .containsEntry("muted", true));
        send(bob, conversation, Map.of("text", "Je reviens vers toi"));
        assertThat(list(alice, "")).singleElement().satisfies(found -> assertThat(found).containsEntry("unread", 3));

        // Once Bob stops following Alice, she reads the past but cannot write; blocked, the conversation is gone for both.
        call(bob, HttpMethod.DELETE, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        ResponseEntity<Map> closed = call(alice, HttpMethod.POST, "/network/conversations/" + conversation + "/messages",
            Map.of("text", "Dommage"), Map.class);
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(messages(alice, conversation)).hasSize(4);
        assertThat(list(alice, "")).singleElement().satisfies(found -> assertThat(found).containsEntry("closed", "NOT_FOLLOWED"));
        assertThat(call(alice, HttpMethod.GET, "/users/export", null, String.class).getBody()).contains("networkMessagesSent");
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/block", null, Map.class);
        assertThat(list(alice, "")).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/network/conversations/" + conversation, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unread(bob)).isEqualTo(0);
    }
}
