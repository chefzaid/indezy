package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The member directory over HTTP: members found by a skill, a city, an availability or remote work, on what their cards
 * show only, never the viewer, nor a member either one blocks.
 */
class NetworkDirectoryIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String join(Account account, String city, List<String> skills, Map<String, Object> fields, Map<String, Object> availability) {
        Map<String, Object> profile = call(account, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        profile.put("city", city);
        profile.put("skills", skills);
        call(account, HttpMethod.PUT, "/users/profile", profile, Map.class);
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", fields, "availability", availability), Map.class);
        return handle;
    }

    @SuppressWarnings("unchecked")
    private List<String> found(Account viewer, String query) {
        Map<String, Object> directory = call(viewer, HttpMethod.GET, "/network/members?" + query, null, Map.class).getBody();
        return ((List<Map<String, Object>>) directory.get("members")).stream().map(card -> (String) card.get("handle")).toList();
    }

    @Test
    void findsMembersByWhatTheirCardsShow() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        assertThat(call(alice, HttpMethod.GET, "/network/members", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String skill = "Skill" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String viewer = join(alice, "Lyon", List.of(skill), ALL_SHOWN, Map.of("mode", "AUTO"));
        Map<String, Object> remote = new HashMap<>(ALL_SHOWN);
        remote.put("remote", true);
        String lyon = join(bob, "Lyon", List.of(skill, "Kafka"), remote, Map.of("mode", "LISTENING"));
        Account carolAccount = register("carol");
        String saintEtienne = join(carolAccount, "Saint-Étienne", List.of(skill), ALL_SHOWN, Map.of("mode", "AVAILABLE"));
        Map<String, Object> hidden = new HashMap<>(ALL_SHOWN);
        hidden.putAll(Map.of("stack", false, "location", false));
        String hiding = join(register("dan"), "Lyon", List.of(skill), hidden, Map.of("mode", "AVAILABLE"));

        // The skill a card hides is never found, nor the viewer.
        assertThat(found(alice, "skill=" + skill.toLowerCase())).containsExactlyInAnyOrder(lyon, saintEtienne).doesNotContain(viewer, hiding);
        assertThat(found(alice, "skill=" + skill + "&city=etienne")).containsExactly(saintEtienne);
        assertThat(found(alice, "skill=" + skill + "&availability=LISTENING")).containsExactly(lyon);
        assertThat(found(alice, "skill=" + skill + "&remote=true")).containsExactly(lyon);
        assertThat(found(alice, "city=lyon&availability=AVAILABLE")).doesNotContain(hiding, lyon);
        assertThat(call(alice, HttpMethod.GET, "/network/members?skill=" + skill + "&page=1", null, Map.class).getBody())
            .containsEntry("total", 2).containsEntry("page", 1).containsEntry("size", 20);

        // Among the members shown, those the viewer follows, has asked to follow, and whose cards are private.
        call(alice, HttpMethod.PUT, "/network/members/" + lyon + "/follow", null, Map.class);
        call(carolAccount, HttpMethod.PUT, "/network/membership/private?private=true", null, Map.class);
        call(alice, HttpMethod.PUT, "/network/members/" + saintEtienne + "/follow", null, Map.class);
        Map<String, Object> relations = call(alice, HttpMethod.GET, "/network/members?skill=" + skill, null, Map.class).getBody();
        assertThat(relations).containsEntry("followed", List.of(lyon)).containsEntry("requested", List.of(saintEtienne))
            .containsEntry("privateCards", List.of(saintEtienne));

        // A member either one blocks leaves the directory both ways.
        call(carolAccount, HttpMethod.PUT, "/network/members/" + viewer + "/block", null, Map.class);
        assertThat(found(alice, "skill=" + skill)).containsExactly(lyon);
        assertThat(found(carolAccount, "skill=" + skill)).containsExactly(lyon);
    }

    @Test
    @SuppressWarnings("unchecked")
    void suggestsMembersToMentionThoseFollowedFirst() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String run = UUID.randomUUID().toString().replace("-", "").substring(0, 6).replaceAll("[0-9]", "x");
        join(alice, "Lyon", List.of("Java"), ALL_SHOWN, Map.of("mode", "AUTO"));
        String first = join(register("zo" + run + "a"), "Lyon", List.of("Java"), ALL_SHOWN, Map.of("mode", "AUTO"));
        String followed = join(register("zo" + run + "b"), "Lyon", List.of("Java"), ALL_SHOWN, Map.of("mode", "AUTO"));
        Account blocker = register("zo" + run + "c");
        String blocking = join(blocker, "Lyon", List.of("Java"), ALL_SHOWN, Map.of("mode", "AUTO"));
        call(alice, HttpMethod.PUT, "/network/members/" + followed + "/follow", null, Map.class);
        call(blocker, HttpMethod.PUT, "/network/members/" + call(alice, HttpMethod.GET, "/network/membership", null, Map.class).getBody()
            .get("handle") + "/block", null, Map.class);

        List<Map<String, Object>> found = call(alice, HttpMethod.GET, "/network/mentions?q=ZO" + run, null, List.class).getBody();
        assertThat(found).extracting(card -> card.get("handle")).containsExactly(followed, first).doesNotContain(blocking);
        assertThat(call(alice, HttpMethod.GET, "/network/mentions?q=" + first.substring(0, 12), null, List.class).getBody())
            .extracting(card -> ((Map<String, Object>) card).get("handle")).contains(first);
        assertThat(call(alice, HttpMethod.GET, "/network/mentions?q=", null, List.class).getBody()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void suggestsMembersToFollowWithTheirReason() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String run = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String skill = "Skill" + run;
        String city = "Ville" + run;
        join(alice, city, List.of(skill), ALL_SHOWN, Map.of("mode", "AUTO"));
        String sameStack = join(bob, "Ailleurs", List.of(skill), ALL_SHOWN, Map.of("mode", "AUTO"));
        Account carol = register("carol");
        String friend = join(carol, "Ailleurs", List.of("Autre"), ALL_SHOWN, Map.of("mode", "AUTO"));
        String friendOfFriend = join(register("dan"), "Ailleurs", List.of("Autre"), ALL_SHOWN, Map.of("mode", "AUTO"));
        String sameCity = join(register("erin"), city, List.of("Autre"), ALL_SHOWN, Map.of("mode", "AUTO"));
        call(alice, HttpMethod.PUT, "/network/members/" + friend + "/follow", null, Map.class);
        call(carol, HttpMethod.PUT, "/network/members/" + friendOfFriend + "/follow", null, Map.class);
        // A contact of Alice's workspace who is a member, matched on the email they sign in with.
        String frankEmail = "frank." + run + "@example.com";
        String contact = join(registerAs(frankEmail), "Ailleurs", List.of("Autre"), ALL_SHOWN, Map.of("mode", "AUTO"));
        call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Frank", "lastName", "Contact", "email", frankEmail.toUpperCase(),
            "clientId", aliceClientId), Map.class);

        List<Map<String, Object>> suggested = call(alice, HttpMethod.GET, "/network/suggestions", null, List.class).getBody();
        Map<String, Map<String, Object>> byHandle = new HashMap<>();
        suggested.forEach(suggestion -> byHandle.put((String) ((Map<String, Object>) suggestion.get("card")).get("handle"), suggestion));
        assertThat(byHandle).doesNotContainKey(friend);
        assertThat(byHandle.get(friendOfFriend)).containsEntry("reason", "FOLLOWED_BY").containsEntry("followedBy", 1)
            .containsEntry("followedByName", "carol");
        assertThat(byHandle.get(sameStack)).containsEntry("reason", "SAME_STACK").containsEntry("sharedSkills", List.of(skill));
        assertThat(byHandle.get(sameCity)).containsEntry("reason", "SAME_CITY");
        assertThat(suggested.indexOf(byHandle.get(friendOfFriend))).isLessThan(suggested.indexOf(byHandle.get(sameCity)));
        assertThat(suggested.getFirst()).containsEntry("reason", "CONTACT");
        assertThat((Map<String, Object>) suggested.getFirst().get("card")).containsEntry("handle", contact);
    }
}
