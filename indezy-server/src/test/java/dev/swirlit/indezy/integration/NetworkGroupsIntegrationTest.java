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
 * Group conversations over HTTP: Alice gathers Bob, who follows her, and Carol, whom she follows; everyone writes and reads who wrote;
 * Alice renames the group and adds Dan; Carol leaves and reads it no more.
 */
class NetworkGroupsIntegrationTest extends TwoAccountsIntegrationTest {

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
    private Map<String, Object> group(Map<String, Object> conversation) {
        return (Map<String, Object>) conversation.get("group");
    }

    @Test
    @SuppressWarnings("unchecked")
    void gathersFollowersWhoAllWriteAndMayLeave() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        Account carol = register("carol");
        String carolHandle = join(carol);
        Account dan = register("dan");
        String danHandle = join(dan);
        Account eve = register("eve");
        join(eve);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        call(dan, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);

        // Every member follows the creator or is followed by them.
        ResponseEntity<Map> refused = call(alice, HttpMethod.POST, "/network/groups", Map.of("title", "Mission Kafka",
            "handles", List.of(bobHandle, carolHandle)), Map.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) refused.getBody().get("message")).endsWith("@" + carolHandle + " is neither");
        assertThat(call(alice, HttpMethod.POST, "/network/groups", Map.of("title", " ", "handles", List.of(bobHandle)), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        call(alice, HttpMethod.PUT, "/network/members/" + carolHandle + "/follow", null, Map.class);
        Map<String, Object> created = call(alice, HttpMethod.POST, "/network/groups", Map.of("title", " Mission Kafka ",
            "handles", List.of(bobHandle, carolHandle, aliceHandle, bobHandle)), Map.class).getBody();
        Object id = created.get("id");
        assertThat(created).containsEntry("with", null).containsEntry("closed", null);
        assertThat(group(created)).containsEntry("title", "Mission Kafka").containsEntry("size", 3).containsEntry("admin", true)
            .containsEntry("creator", aliceHandle);
        assertThat((List<Map<String, Object>>) group(created).get("members")).extracting(member -> member.get("handle"))
            .containsExactlyInAnyOrder(bobHandle, carolHandle);

        // Everyone writes; each reads who wrote; the group shows by its name in the list, the search and the PDF.
        call(bob, HttpMethod.POST, "/network/conversations/" + id + "/messages", Map.of("text", "Bonjour l'équipe"), Map.class);
        Map<String, Object> listed = ((List<Map<String, Object>>) call(carol, HttpMethod.GET, "/network/conversations", null, List.class)
            .getBody()).stream().filter(found -> id.equals(found.get("id"))).findFirst().orElseThrow();
        assertThat(listed).containsEntry("unread", 1);
        assertThat(group(listed)).containsEntry("admin", false);
        List<Map<String, Object>> read = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/network/conversations/" + id, null, Map.class)
            .getBody().get("messages");
        assertThat((Map<String, Object>) read.getFirst().get("from")).containsEntry("handle", bobHandle);
        Map<String, Object> mine = call(carol, HttpMethod.POST, "/network/conversations/" + id + "/messages", Map.of("text", "Présente !"),
            Map.class).getBody();
        assertThat(mine).containsEntry("from", null).containsEntry("mine", true);
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/network/conversations/search?q=équipe", null, List.class).getBody())
            .singleElement().satisfies(hit -> assertThat(hit).containsEntry("title", "Mission Kafka").containsEntry("conversationId", id));
        assertThat(call(alice, HttpMethod.GET, "/network/conversations/" + id + "/pdf", null, byte[].class).getHeaders()
            .getContentDisposition().getFilename()).startsWith("conversation-group-" + id + "-");
        assertThat(call(alice, HttpMethod.GET, "/network/conversations/unread", null, Map.class).getBody()).containsEntry("conversations", 1);

        // Only Alice renames the group and adds members, who follow her or whom she follows; outsiders read nothing.
        assertThat(call(bob, HttpMethod.PUT, "/network/groups/" + id + "/title", Map.of("title", "À moi"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(group(call(alice, HttpMethod.PUT, "/network/groups/" + id + "/title", Map.of("title", "Équipe Kafka"), Map.class).getBody()))
            .containsEntry("title", "Équipe Kafka");
        assertThat(call(bob, HttpMethod.POST, "/network/groups/" + id + "/members", Map.of("handles", List.of(danHandle)), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(group(call(alice, HttpMethod.POST, "/network/groups/" + id + "/members", Map.of("handles", List.of(danHandle, bobHandle)),
            Map.class).getBody())).containsEntry("size", 4);
        assertThat(call(eve, HttpMethod.GET, "/network/conversations/" + id, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(eve, HttpMethod.POST, "/network/conversations/" + id + "/messages", Map.of("text", "Moi aussi"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(eve, HttpMethod.DELETE, "/network/groups/" + id + "/members/me", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // Carol leaves: she reads it no more, the others go on.
        assertThat(call(carol, HttpMethod.DELETE, "/network/groups/" + id + "/members/me", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(carol, HttpMethod.GET, "/network/conversations/" + id, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> thread = call(dan, HttpMethod.GET, "/network/conversations/" + id, null, Map.class).getBody();
        assertThat(group((Map<String, Object>) thread.get("conversation"))).containsEntry("size", 3);
        assertThat(call(alice, HttpMethod.POST, "/network/conversations/" + id + "/messages", Map.of("text", "Bienvenue Dan"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
