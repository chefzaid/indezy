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
 * Reports and the moderation queue over HTTP: Bob and Carol report Alice's post, a comment and Alice herself; the
 * administrator removes the post (both reports decided at once), warns, suspends and bans; everyone is told.
 */
@TestPropertySource(properties = "indezy.admin.emails=" + AdminIntegrationTest.CHIEF)
class NetworkModerationIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void closeTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, false);
    }

    private String join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    private HttpStatus report(Account account, String path, Object body) {
        return HttpStatus.valueOf(call(account, HttpMethod.POST, path, body, String.class).getStatusCode().value());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> open(Account admin, String type, Object targetId) {
        List<Map<String, Object>> open = (List<Map<String, Object>>) call(admin, HttpMethod.GET, "/admin/network/reports", null, Map.class)
            .getBody().get("open");
        return open.stream().filter(report -> type.equals(report.get("targetType")))
            .filter(report -> String.valueOf(report.get("targetId")).equals(String.valueOf(targetId))).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Object> notices(Account account) {
        return call(account, HttpMethod.GET, "/network/notifications", null, List.class).getBody().stream()
            .map(group -> ((Map<String, Object>) group).get("type")).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsAreDecidedTogetherAndTheSanctionsApply() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        Account carol = register("carol");
        String aliceHandle = join(alice);
        join(bob);
        join(carol);
        Account admin = chief();
        Object post = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Achetez mes formations"), Map.class).getBody().get("id");
        Object comment = call(alice, HttpMethod.POST, "/network/posts/" + post + "/comments", Map.of("text", "Lien en bio"), Map.class)
            .getBody().get("id");

        // Reports: once while open, never of oneself, with a known reason, of what the reporter can see.
        Map<String, Object> spam = Map.of("reason", "SPAM", "details", "  Publicité  ");
        assertThat(report(bob, "/network/posts/" + post + "/report", spam)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(report(bob, "/network/posts/" + post + "/report", spam)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(report(carol, "/network/posts/" + post + "/report", Map.of("reason", "MISLEADING"))).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(report(alice, "/network/posts/" + post + "/report", spam)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(report(bob, "/network/posts/" + post + "/report", Map.of("reason", "BORING"))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(report(bob, "/network/posts/" + post + "/report", Map.of("reason", "SPAM", "details", "x".repeat(501))))
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(report(bob, "/network/posts/999999999/report", spam)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(report(bob, "/network/comments/" + comment + "/report", Map.of("reason", "HARASSMENT"))).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(report(bob, "/network/members/" + aliceHandle + "/report", Map.of("reason", "IMPERSONATION")))
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(report(bob, "/network/members/nobody_here/report", spam)).isEqualTo(HttpStatus.NOT_FOUND);

        // Only administrators see the queue; the post's two reports show together, with the text as it read.
        assertThat(call(bob, HttpMethod.GET, "/admin/network/reports", null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        List<Map<String, Object>> onPost = open(admin, "POST", post);
        assertThat(onPost).hasSize(2).allSatisfy(found -> assertThat(found).containsEntry("targetType", "POST")
            .containsEntry("excerpt", "Achetez mes formations").containsEntry("openOnTarget", 2));
        assertThat(onPost.getFirst()).containsEntry("details", "Publicité").containsEntry("reason", "SPAM");
        assertThat((Map<String, Object>) onPost.getFirst().get("target")).containsEntry("handle", aliceHandle);
        Object postReport = onPost.getFirst().get("id");
        assertThat(call(bob, HttpMethod.PUT, "/admin/network/reports/" + postReport, Map.of("outcome", "REMOVED"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Removing the post decides both reports, deletes the post, tells Alice and both reporters.
        call(admin, HttpMethod.PUT, "/admin/network/reports/" + postReport, Map.of("outcome", "REMOVED", "note", "Spam commercial"), Map.class);
        assertThat(open(admin, "POST", post)).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/network/posts/" + post + "/comments", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(admin, HttpMethod.PUT, "/admin/network/reports/" + postReport, Map.of("outcome", "WARNED"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        List<Map<String, Object>> resolved = (List<Map<String, Object>>) call(admin, HttpMethod.GET, "/admin/network/reports", null, Map.class)
            .getBody().get("resolved");
        assertThat(resolved).filteredOn(found -> "POST".equals(found.get("targetType")))
            .filteredOn(found -> String.valueOf(found.get("targetId")).equals(String.valueOf(post))).hasSize(2)
            .allSatisfy(found -> assertThat(found).containsEntry("outcome", "REMOVED").containsEntry("note", "Spam commercial"));
        assertThat(notices(alice)).contains("CONTENT_REMOVED");
        assertThat(notices(bob)).contains("REPORT_ACTED");
        assertThat(notices(carol)).contains("REPORT_ACTED");

        // The comment went with its post; its report is dismissed. A member cannot be removed, only warned and the like.
        Object commentReport = open(admin, "COMMENT", comment).getFirst().get("id");
        call(admin, HttpMethod.PUT, "/admin/network/reports/" + commentReport, Map.of("outcome", "DISMISSED"), Map.class);
        Object memberReport = open(admin, "MEMBER", alice.freelanceId()).getFirst().get("id");
        assertThat(call(admin, HttpMethod.PUT, "/admin/network/reports/" + memberReport, Map.of("outcome", "REMOVED"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(admin, HttpMethod.PUT, "/admin/network/reports/" + memberReport, Map.of("outcome", "SUSPENDED", "days", 91),
            String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Suspended, Alice can still read but neither post nor follow; the sanction is in her activity log.
        call(admin, HttpMethod.PUT, "/admin/network/reports/" + memberReport, Map.of("outcome", "SUSPENDED", "days", 3), Map.class);
        assertThat(notices(alice)).contains("SUSPENDED");
        assertThat(notices(bob)).contains("REPORT_DISMISSED");
        ResponseEntity<Map> refused = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Encore"), Map.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) refused.getBody().get("message")).startsWith("The moderators suspended you until");
        assertThat(call(alice, HttpMethod.GET, "/network/feed", null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(alice, HttpMethod.GET, "/users/activity", null, String.class).getBody()).contains("NETWORK_SANCTIONED");

        // Banned, Alice is out of the network and cannot join again.
        assertThat(report(carol, "/network/members/" + aliceHandle + "/report", Map.of("reason", "HARASSMENT")))
            .isEqualTo(HttpStatus.NO_CONTENT);
        Object again = open(admin, "MEMBER", alice.freelanceId()).getFirst().get("id");
        call(admin, HttpMethod.PUT, "/admin/network/reports/" + again, Map.of("outcome", "BANNED"), Map.class);
        assertThat(call(alice, HttpMethod.GET, "/network/membership", null, Map.class).getBody()).containsEntry("joined", false);
        assertThat(call(bob, HttpMethod.GET, "/network/members/" + aliceHandle, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<Map> rejoin = call(alice, HttpMethod.PUT, "/network/membership", Map.of("handle", aliceHandle, "fields", ALL_SHOWN),
            Map.class);
        assertThat(rejoin.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejoin.getBody()).containsEntry("message", "The moderators ended this account's membership");
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
}
