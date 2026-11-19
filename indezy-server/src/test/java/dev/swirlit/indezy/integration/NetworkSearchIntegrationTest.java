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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The network's search over HTTP: Bob finds members by their whole name, a word of it, a skill or their city, those named
 * by name first, never one who blocks him; and the posts holding what he typed, case aside, but those of a member he mutes.
 */
class NetworkSearchIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String join(Account account, String firstName, String lastName, String city, List<String> skills) {
        Map<String, Object> profile = call(account, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        profile.put("firstName", firstName);
        profile.put("lastName", lastName);
        profile.put("city", city);
        profile.put("skills", skills);
        call(account, HttpMethod.PUT, "/users/profile", profile, Map.class);
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    @SuppressWarnings("unchecked")
    private List<String> members(Account viewer, String query) {
        Map<String, Object> found = call(viewer, HttpMethod.GET, "/network/search/members?q=" + query, null, Map.class).getBody();
        return ((List<Map<String, Object>>) found.get("members")).stream().map(card -> (String) card.get("handle")).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Object> posts(Account viewer, String query) {
        Map<String, Object> found = call(viewer, HttpMethod.GET, "/network/search/posts?q=" + query, null, Map.class).getBody();
        return ((List<Map<String, Object>>) found.get("posts")).stream().map(post -> post.get("id")).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void findsMembersAndPostsByWhatWasTyped() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        // A word no other test's member or post holds: letters only, as names and words are.
        String word = "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).chars()
            .mapToObj(c -> String.valueOf((char) ('a' + c % 26))).collect(Collectors.joining());
        String skill = "Skill" + word;
        String bobHandle = join(bob, "Bob", "Martin", "Lyon", List.of("Java"));
        String named = join(alice, "Élodie", word + "-Durand", "Nantes", List.of("Kafka"));
        Account carol = register("carol");
        String skilled = join(carol, "Carol", "Petit", "Lyon", List.of(skill));
        Account dan = register("dan");
        String blocker = join(dan, "Dan", word, "Lyon", List.of(skill));
        call(dan, HttpMethod.PUT, "/network/members/" + bobHandle + "/block", null, Map.class);

        // A word of a compound last name, the whole name accents aside, a skill, by name first; never a member blocking Bob.
        assertThat(members(bob, word)).containsExactly(named);
        assertThat(members(bob, "elodie " + word)).containsExactly(named);
        assertThat(members(bob, skill.toUpperCase())).containsExactly(skilled);
        assertThat(members(bob, "carol lyon")).contains(skilled);
        assertThat(members(bob, "")).isEmpty();
        // Mentions take every word typed on the names.
        Object mention = call(bob, HttpMethod.GET, "/network/mentions?q=elod " + word.substring(0, 4), null, List.class).getBody();
        assertThat((List<Map<String, Object>>) mention).extracting(card -> card.get("handle")).containsExactly(named);

        Object kafka = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Retour sur " + word + " et Kafka"), Map.class)
            .getBody().get("id");
        Object own = call(bob, HttpMethod.POST, "/network/posts", Map.of("text", "Mon avis sur " + word.toUpperCase()), Map.class)
            .getBody().get("id");
        Object carols = call(carol, HttpMethod.POST, "/network/posts", Map.of("text", "Atelier " + word + " 100"), Map.class)
            .getBody().get("id");
        assertThat(posts(bob, word)).containsExactly(carols, own, kafka);
        assertThat(posts(bob, word + " et kafka")).containsExactly(kafka);
        // The pattern's own characters are searched as written: an underscore is no wildcard.
        assertThat(posts(bob, word + " 100")).containsExactly(carols);
        assertThat(posts(bob, word + " 1_0")).isEmpty();
        assertThat(posts(bob, "a")).isEmpty();
        call(bob, HttpMethod.PUT, "/network/members/" + skilled + "/mute", null, Map.class);
        assertThat(posts(bob, word)).containsExactly(own, kafka);
        // Filters: a city, remote work, an availability; posts of a kind, recent, from members followed.
        assertThat(members(bob, "carol&city=lyo")).contains(skilled);
        assertThat(members(bob, "carol&city=nantes")).doesNotContain(skilled);
        assertThat(members(bob, word + "&remote=true")).isEmpty();
        assertThat(members(bob, word + "&availability=LISTENING")).isEmpty();
        Object mission = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Mission " + word, "kind", "MISSION", "details",
            Map.of("role", "Lead Java")), Map.class).getBody().get("id");
        assertThat(posts(bob, word + "&kind=MISSION")).containsExactly(mission);
        assertThat(posts(bob, word + "&kind=MISSION&kind=POST&days=7")).contains(mission, own, kafka);
        call(bob, HttpMethod.PUT, "/network/members/" + named + "/follow", null, Map.class);
        assertThat(posts(bob, word + "&followed=true")).containsExactly(mission, kafka);
        assertThat(blocker).isNotBlank();
    }
}
