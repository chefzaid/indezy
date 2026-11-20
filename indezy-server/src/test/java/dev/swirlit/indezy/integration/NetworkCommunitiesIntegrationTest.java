package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Communities over HTTP: Alice creates one and moderates it, Bob and Carol join and post, Alice names Bob a co-moderator,
 * Bob removes Carol's post and Carol (who cannot join again), not Alice; an administrator moderates it too and deletes it.
 */
@TestPropertySource(properties = "indezy.admin.emails=" + AdminIntegrationTest.CHIEF)
class NetworkCommunitiesIntegrationTest extends TwoAccountsIntegrationTest {

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

    private HttpStatus status(Account account, HttpMethod method, String path, Object body) {
        return HttpStatus.valueOf(call(account, method, path, body, String.class).getStatusCode().value());
    }

    @SuppressWarnings("unchecked")
    private List<Object> posts(Account reader, String path) {
        return ((List<Map<String, Object>>) call(reader, HttpMethod.GET, path + "/posts", null, Map.class).getBody().get("posts")).stream()
            .map(post -> post.get("id")).toList();
    }

    private Account chief() {
        ResponseEntity<Map> signedIn = login(AdminIntegrationTest.CHIEF);
        if (signedIn.getStatusCode() != HttpStatus.OK) {
            registerAs(AdminIntegrationTest.CHIEF);
            signedIn = login(AdminIntegrationTest.CHIEF);
        }
        Map<?, ?> user = (Map<?, ?>) signedIn.getBody().get("user");
        return new Account((String) signedIn.getBody().get("token"), ((Number) user.get("id")).longValue());
    }

    private ResponseEntity<Map> login(String email) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        return post("/auth/login", request);
    }

    @Test
    @SuppressWarnings("unchecked")
    void runsACommunityWithItsModerators() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        Account carol = register("carol");
        String carolHandle = join(carol);
        String name = "Kafka à Lyon " + UUID.randomUUID().toString().substring(0, 4);

        Map<String, Object> created = call(alice, HttpMethod.POST, "/network/communities", Map.of("name", name, "description", "Les fans"),
            Map.class).getBody();
        String slug = (String) created.get("slug");
        assertThat(slug).startsWith("kafka-a-lyon-");
        assertThat(created).containsEntry("members", 1).containsEntry("joined", true).containsEntry("moderator", true);
        String path = "/network/communities/" + slug;

        // Members post once in; a member outside cannot.
        assertThat(status(bob, HttpMethod.POST, path + "/posts", Map.of("text", "Bonjour"))).isEqualTo(HttpStatus.FORBIDDEN);
        call(bob, HttpMethod.PUT, path + "/membership", null, Map.class);
        call(carol, HttpMethod.PUT, path + "/membership", null, Map.class);
        Object bobs = call(bob, HttpMethod.POST, path + "/posts", Map.of("text", "Qui utilise Kafka Streams ?"), Map.class).getBody().get("id");
        Object carols = call(carol, HttpMethod.POST, path + "/posts", Map.of("text", "Une offre hors sujet"), Map.class).getBody().get("id");
        assertThat(posts(alice, path)).containsExactly(carols, bobs);
        List<Map<String, Object>> listed = call(bob, HttpMethod.GET, "/network/communities?q=kafka a lyon", null, List.class).getBody();
        assertThat(listed).anySatisfy(community -> assertThat(community).containsEntry("slug", slug).containsEntry("members", 3)
            .containsEntry("joined", true).containsEntry("moderator", false));

        // Only the creator names co-moderators; a co-moderator removes posts and members, never the creator.
        assertThat(status(bob, HttpMethod.DELETE, path + "/posts/" + carols, null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(carol, HttpMethod.PUT, path + "/moderators/" + bobHandle, null)).isEqualTo(HttpStatus.FORBIDDEN);
        List<Map<String, Object>> members = call(alice, HttpMethod.PUT, path + "/moderators/" + bobHandle, null, List.class).getBody();
        assertThat(members).filteredOn(member -> Boolean.TRUE.equals(member.get("moderator"))).hasSize(2);
        assertThat(status(bob, HttpMethod.DELETE, path + "/posts/" + carols, null)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(posts(alice, path)).containsExactly(bobs);
        assertThat(status(bob, HttpMethod.DELETE, path + "/members/" + aliceHandle, null)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(bob, HttpMethod.DELETE, path + "/members/" + carolHandle, null)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(status(carol, HttpMethod.PUT, path + "/membership", null)).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> page = call(bob, HttpMethod.GET, path, null, Map.class).getBody();
        assertThat((Map<String, Object>) page.get("community")).containsEntry("members", 2).containsEntry("moderator", true);
        assertThat(page).containsEntry("creator", false);
        assertThat((List<Object>) page.get("moderators")).hasSize(2);

        // The administrators moderate every community and keep the last word.
        Account admin = chief();
        join(admin);
        assertThat(call(admin, HttpMethod.GET, path, null, Map.class).getBody()).containsEntry("creator", true);
        assertThat(status(admin, HttpMethod.DELETE, path + "/posts/" + bobs, null)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(status(bob, HttpMethod.DELETE, path, null)).isEqualTo(HttpStatus.FORBIDDEN);
        Object kept = call(bob, HttpMethod.POST, path + "/posts", Map.of("text", "Avant la fermeture"), Map.class).getBody().get("id");
        assertThat(status(admin, HttpMethod.DELETE, path, null)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(status(bob, HttpMethod.GET, path, null)).isEqualTo(HttpStatus.NOT_FOUND);
        // Its posts stay as plain posts.
        assertThat(status(bob, HttpMethod.GET, "/network/posts/" + kept, null)).isEqualTo(HttpStatus.OK);
    }
}
