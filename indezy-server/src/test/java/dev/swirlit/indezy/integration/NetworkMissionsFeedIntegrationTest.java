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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The feed's Missions tab over HTTP: Bob reads the missions every member passes on, whether he follows them or not, the
 * newest first, but his own and those of a member he mutes; nothing else than missions.
 */
class NetworkMissionsFeedIntegrationTest extends TwoAccountsIntegrationTest {

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

    private Object mission(Account author, String role) {
        return call(author, HttpMethod.POST, "/network/posts", Map.of("text", "Je ne peux pas la prendre", "kind", "MISSION", "details",
            Map.of("role", role, "rateMin", 600, "rateMax", 700, "city", "Lyon")), Map.class).getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private List<Object> missions(Account reader) {
        Map<String, Object> page = call(reader, HttpMethod.GET, "/network/feed/missions", null, Map.class).getBody();
        return ((List<Map<String, Object>>) page.get("posts")).stream().map(post -> post.get("id")).toList();
    }

    @Test
    void listsTheMissionsPassedOnByEveryMember() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        join(bob);
        Account carol = register("carol");
        String carolHandle = join(carol);
        Object kafka = mission(alice, "Lead Kafka");
        call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Un simple message"), Map.class);
        Object angular = mission(carol, "Lead Angular");
        Object own = mission(bob, "Son propre poste");

        // Bob follows nobody: the missions of every member reach him, the newest first, but his own (other tests' too).
        assertThat(missions(bob)).containsSubsequence(angular, kafka).doesNotContain(own);
        call(bob, HttpMethod.PUT, "/network/members/" + carolHandle + "/mute", null, Map.class);
        assertThat(missions(bob)).contains(kafka).doesNotContain(angular, own);
        assertThat(aliceHandle).isNotBlank();
    }

    @SuppressWarnings("unchecked")
    private List<Object> missions(Account reader, String query) {
        Map<String, Object> page = call(reader, HttpMethod.GET, "/network/feed/missions?" + query, null, Map.class).getBody();
        return ((List<Map<String, Object>>) page.get("posts")).stream().map(post -> post.get("id")).toList();
    }

    private String joinWith(Account account, String city, List<String> skills) {
        Map<String, Object> profile = call(account, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        profile.put("city", city);
        profile.put("skills", skills);
        call(account, HttpMethod.PUT, "/users/profile", profile, Map.class);
        return join(account);
    }

    @SuppressWarnings("unchecked")
    private List<Object> told(Account reader) {
        return ((List<Map<String, Object>>) call(reader, HttpMethod.GET, "/network/notifications", null, List.class).getBody()).stream()
            .filter(group -> "MISSION_MATCH".equals(group.get("type"))).map(group -> group.get("postId")).toList();
    }

    @Test
    void narrowsTheMissionsAndTellsTheMembersTheyMatch() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String skill = "Skill" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        joinWith(alice, "Lyon", List.of("Java"));
        joinWith(bob, "Lyon", List.of(skill, "Java"));
        Account carol = register("carol");
        joinWith(carol, "Brest", List.of(skill));
        Object lyon = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Mission à Lyon", "kind", "MISSION", "details",
            Map.of("role", "Lead " + skill, "rateMin", 600, "rateMax", 700, "city", "Lyon", "remoteDays", 2, "start", "2027-01-04",
                "stack", List.of(skill))), Map.class).getBody().get("id");
        Object remote = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Mission à distance", "kind", "MISSION", "details",
            Map.of("role", "Dev " + skill, "rateMin", 450, "rateMax", 500, "city", "Paris", "remoteDays", 5, "stack", List.of(skill))), Map.class)
            .getBody().get("id");

        // Filters: a skill, a city, remote days, a rate reached, a start, and the missions already taken.
        assertThat(missions(bob, "skill=" + skill.toLowerCase())).containsExactly(remote, lyon);
        assertThat(missions(bob, "skill=" + skill + "&city=lyon")).containsExactly(lyon);
        assertThat(missions(bob, "skill=" + skill + "&remote=3")).containsExactly(remote);
        assertThat(missions(bob, "skill=" + skill + "&rate=650")).containsExactly(lyon);
        assertThat(missions(bob, "skill=" + skill + "&before=2026-12-31")).containsExactly(remote);
        call(bob, HttpMethod.POST, "/network/posts/" + lyon + "/pipeline?named=false", null, Map.class);
        assertThat(missions(bob, "skill=" + skill + "&hideTaken=true")).containsExactly(remote);

        // Alerts: a skill the card shows, and the city unless fully remote.
        assertThat(told(bob)).contains(lyon, remote);
        assertThat(told(carol)).contains(remote).doesNotContain(lyon);
        assertThat(told(alice)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ranksTheMissionsByHowTheyFitTheReadersProfile() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        Map<String, Object> profile = call(bob, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        profile.put("skills", List.of("Kafka", "Java"));
        profile.put("targetDailyRate", 650);
        call(bob, HttpMethod.PUT, "/users/profile", profile, Map.class);
        join(bob);
        Object kafka = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Mission Kafka", "kind", "MISSION", "details",
            Map.of("role", "Lead Kafka", "stack", List.of("Java", "Kafka"), "rateMin", 600, "rateMax", 700, "remoteDays", 5)), Map.class).getBody().get("id");
        Object angular = mission(alice, "Lead Angular");

        // Newest first as usual; the best fit first when asked.
        assertThat(missions(bob)).containsSubsequence(angular, kafka);
        assertThat(missions(bob, "bestMatch=true")).containsSubsequence(kafka, angular);
        Map<String, Map<String, Object>> matches = call(bob, HttpMethod.GET, "/network/feed/missions/matches?ids=" + kafka + "," + angular,
            null, Map.class).getBody();
        // Kafka and Java (40 of 60 skill points with two skills, full marks), the rate met: 85 of 85 points.
        assertThat(matches.get(String.valueOf(kafka))).containsEntry("score", 100).containsEntry("skills", List.of("Kafka", "Java"))
            .containsEntry("rate", "MEETS");
        assertThat(matches.get(String.valueOf(angular))).containsEntry("score", 29);
    }
}
