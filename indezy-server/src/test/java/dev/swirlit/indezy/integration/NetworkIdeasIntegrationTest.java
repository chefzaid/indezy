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
 * The ideas board over HTTP: Alice proposes two ideas and Bob votes for one, which ranks it first; Bob may not change or
 * delete Alice's; an administrator plans one (its voters told) and merges the other into it, moving its votes.
 */
@TestPropertySource(properties = "indezy.admin.emails=" + AdminIntegrationTest.CHIEF)
class NetworkIdeasIntegrationTest extends TwoAccountsIntegrationTest {

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

    private HttpStatus status(Account account, HttpMethod method, String path, Object body) {
        return HttpStatus.valueOf(call(account, method, path, body, String.class).getStatusCode().value());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> board(Account reader, String query) {
        return (List<Map<String, Object>>) call(reader, HttpMethod.GET, "/network/ideas?q=" + query, null, Map.class).getBody().get("ideas");
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
    void proposesVotesAnswersAndMergesIdeas() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        join(alice);
        join(bob);
        String word = "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        ResponseEntity<Map> first = call(alice, HttpMethod.POST, "/network/ideas", Map.of("title", "Export CSV " + word, "description", "Des factures"), Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).containsEntry("votes", 1).containsEntry("voted", true).containsEntry("own", true).containsEntry("status", "OPEN");
        Object exportId = first.getBody().get("id");
        Object darkId = call(alice, HttpMethod.POST, "/network/ideas", Map.of("title", "Thème sombre " + word), Map.class).getBody().get("id");
        assertThat(status(alice, HttpMethod.POST, "/network/ideas", Map.of("title", "Non"))).isEqualTo(HttpStatus.BAD_REQUEST);

        // Bob's vote ranks the dark theme first; the newest order puts it first anyway.
        Map<String, Object> voted = call(bob, HttpMethod.PUT, "/network/ideas/" + darkId + "/vote", null, Map.class).getBody();
        assertThat(voted).containsEntry("votes", 2).containsEntry("voted", true).containsEntry("own", false);
        call(bob, HttpMethod.PUT, "/network/ideas/" + darkId + "/vote", null, Map.class);
        assertThat(board(bob, word)).extracting(idea -> idea.get("id")).containsExactly(darkId, exportId);
        assertThat(board(bob, "factures%20" + word)).isEmpty();
        assertThat(board(bob, "sombre")).extracting(idea -> idea.get("id")).contains(darkId).doesNotContain(exportId);
        assertThat(call(bob, HttpMethod.GET, "/network/ideas", null, Map.class).getBody()).containsEntry("administrator", false);

        // Only Alice changes her idea, and she may not delete one Bob voted for; an administrator answers.
        assertThat(status(bob, HttpMethod.PUT, "/network/ideas/" + exportId, Map.of("title", "Autre chose"))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(alice, HttpMethod.DELETE, "/network/ideas/" + darkId, null)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(status(bob, HttpMethod.PUT, "/network/ideas/" + darkId + "/status", Map.of("status", "PLANNED"))).isEqualTo(HttpStatus.FORBIDDEN);
        Account admin = chief();
        join(admin);
        Map<String, Object> planned = call(admin, HttpMethod.PUT, "/network/ideas/" + darkId + "/status", Map.of("status", "PLANNED", "note", "Au printemps"),
            Map.class).getBody();
        assertThat(planned).containsEntry("status", "PLANNED").containsEntry("statusNote", "Au printemps");
        List<Map<String, Object>> notices = call(bob, HttpMethod.GET, "/network/notifications", null, List.class).getBody();
        assertThat(notices).anySatisfy(notice -> {
            assertThat(notice).containsEntry("type", "IDEA_STATUS");
            assertThat((Map<String, Object>) notice.get("idea")).containsEntry("id", darkId).containsEntry("status", "PLANNED");
        });

        // The export idea merged into the dark theme: its votes move there, Alice's counted once.
        Map<String, Object> kept = call(admin, HttpMethod.PUT, "/network/ideas/" + exportId + "/merge?into=" + darkId, null, Map.class).getBody();
        assertThat(kept).containsEntry("id", darkId).containsEntry("votes", 2);
        Map<String, Object> merged = call(alice, HttpMethod.GET, "/network/ideas/" + exportId, null, Map.class).getBody();
        assertThat(merged).containsEntry("status", "MERGED");
        assertThat((Map<String, Object>) merged.get("mergedInto")).containsEntry("id", darkId);
        assertThat(board(alice, word)).extracting(idea -> idea.get("id")).containsExactly(darkId);
        assertThat(status(bob, HttpMethod.PUT, "/network/ideas/" + exportId + "/vote", null)).isEqualTo(HttpStatus.CONFLICT);

        // Bob takes his vote back; the administrator deletes the idea.
        assertThat(call(bob, HttpMethod.DELETE, "/network/ideas/" + darkId + "/vote", null, Map.class).getBody()).containsEntry("votes", 1)
            .containsEntry("voted", false);
        assertThat(status(admin, HttpMethod.DELETE, "/network/ideas/" + darkId, null)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(status(alice, HttpMethod.GET, "/network/ideas/" + darkId, null)).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
