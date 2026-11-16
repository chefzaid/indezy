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

/** Kinds of post over HTTP: a mission to pass on and an availability with their facts, and a question Alice marks answered. */
class NetworkPostKindsIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private void join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
    }

    private HttpStatus post(Account account, Map<String, Object> body) {
        return HttpStatus.valueOf(call(account, HttpMethod.POST, "/network/posts", body, String.class).getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void postsMissionsAvailabilitiesAndAnsweredQuestions() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        join(bob);
        Map<String, Object> details = new HashMap<>(Map.of("role", " Lead Java ", "rateMin", 600, "rateMax", 700, "city", "Lyon",
            "remoteDays", 2, "start", "2027-01-04", "client", "Acme", "stack", List.of("Java", "Kafka", "Java")));
        Map<String, Object> mission = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Je ne peux pas la prendre",
            "kind", "MISSION", "details", details), Map.class).getBody();
        assertThat(mission).containsEntry("kind", "MISSION");
        assertThat((Map<String, Object>) mission.get("details")).containsEntry("role", "Lead Java").containsEntry("rateMin", 600)
            .containsEntry("city", "Lyon").containsEntry("remoteDays", 2).containsEntry("start", "2027-01-04").containsEntry("client", "Acme")
            .containsEntry("stack", List.of("Java", "Kafka"));

        assertThat(post(alice, Map.of("text", "Sans rôle", "kind", "MISSION", "details", Map.of("city", "Lyon")))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(alice, Map.of("text", "Sans rien", "kind", "MISSION"))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(alice, Map.of("text", "Inversé", "kind", "MISSION", "details", Map.of("role", "Dev", "rateMin", 800, "rateMax", 600))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(alice, Map.of("text", "Six jours", "kind", "MISSION", "details", Map.of("role", "Dev", "remoteDays", 6))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(alice, Map.of("text", "Quand ?", "kind", "AVAILABLE", "details", Map.of("rateMin", 500))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> available = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Disponible bientôt", "kind", "AVAILABLE",
            "details", Map.of("start", "2027-02-01", "client", "ignoré", "stack", List.of("Angular"))), Map.class).getBody();
        assertThat((Map<String, Object>) available.get("details")).containsEntry("start", "2027-02-01").containsEntry("client", null);
        Map<String, Object> plain = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Bonjour", "kind", "EXPERIENCE",
            "details", Map.of("role", "ignoré")), Map.class).getBody();
        assertThat(plain).containsEntry("kind", "EXPERIENCE").containsEntry("details", null);

        // A question: Alice picks Bob's reply as its answer; only hers, only a comment under it; gone with the comment.
        Object question = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Quel ORM ?", "kind", "QUESTION"), Map.class)
            .getBody().get("id");
        Object reply = call(bob, HttpMethod.POST, "/network/posts/" + question + "/comments", Map.of("text", "jOOQ"), Map.class).getBody()
            .get("id");
        Object elsewhere = call(bob, HttpMethod.POST, "/network/posts/" + mission.get("id") + "/comments", Map.of("text", "Intéressé"),
            Map.class).getBody().get("id");
        assertThat(call(bob, HttpMethod.PUT, "/network/posts/" + question + "/answer?commentId=" + reply, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.PUT, "/network/posts/" + mission.get("id") + "/answer?commentId=" + elsewhere, null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.PUT, "/network/posts/" + question + "/answer?commentId=" + elsewhere, null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        call(alice, HttpMethod.PUT, "/network/posts/" + question + "/answer?commentId=" + reply, null, String.class);
        String handle = (String) ((Map<String, Object>) mission.get("author")).get("handle");
        List<Map<String, Object>> posts = (List<Map<String, Object>>) call(bob, HttpMethod.GET, "/network/members/" + handle + "/posts", null,
            Map.class).getBody().get("posts");
        assertThat(posts).filteredOn(found -> found.get("id").equals(question)).singleElement()
            .satisfies(found -> assertThat(found).containsEntry("answerCommentId", reply));
        call(bob, HttpMethod.DELETE, "/network/comments/" + reply, null, String.class);
        posts = (List<Map<String, Object>>) call(bob, HttpMethod.GET, "/network/members/" + handle + "/posts", null, Map.class).getBody()
            .get("posts");
        assertThat(posts).filteredOn(found -> found.get("id").equals(question)).singleElement()
            .satisfies(found -> assertThat(found).containsEntry("answerCommentId", null));
    }
}
