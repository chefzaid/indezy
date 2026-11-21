package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Members' reviews of the clients and ESNs they worked with, over HTTP: written from a won mission's client, held when they
 * look like personal data or an accusation, published or rejected by an administrator, met across workspaces by SIREN,
 * reported and removed.
 */
@TestPropertySource(properties = "indezy.admin.emails=" + AdminIntegrationTest.CHIEF)
class NetworkReviewIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    private void join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
    }

    private long client(Account account, String name, String siren, boolean isFinal) {
        return idOf(call(account, HttpMethod.POST, "/clients", Map.of("companyName", name, "city", "Paris", "isFinal", isFinal, "siren", siren,
            "freelanceId", account.freelanceId()), Map.class));
    }

    private void won(Account account, long middleman) {
        call(account, HttpMethod.POST, "/projects", Map.of("role", "Java lead", "dailyRate", 600, "status", "WON", "clientId", aliceClientId,
            "middlemanId", middleman, "freelanceId", account.freelanceId()), Map.class);
    }

    private static Map<String, Object> review(String text) {
        return Map.of("paymentRating", 2, "processRating", 4, "rateRating", 3, "text", text);
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesHoldsPublishesReportsAndRemovesAReview() {
        join(alice);
        join(bob);
        Account chief = chief();
        long aliceEsn = client(alice, "Capgemini", "330703844", false);
        long bobEsn = client(bob, "CAPGEMINI SA", "330 703 844", false);
        call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Jean", "lastName", "Dupontel", "email", "jean@capgemini.example",
            "phone", "0601020304", "clientId", aliceEsn, "freelanceId", alice.freelanceId()), Map.class);
        String path = "/network/reviews/client/" + aliceEsn;

        // Without a won mission with it, nothing to review yet; Bob cannot read Alice's client.
        assertThat(call(alice, HttpMethod.GET, path, null, Map.class).getBody()).containsEntry("canReview", false).containsEntry("count", 0)
            .containsEntry("kind", "ESN").containsEntry("mine", null);
        String fair = "Paid at 45 days, process quick, the rate announced was the one signed.";
        assertThat(call(alice, HttpMethod.PUT, path, review(fair), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(bob, HttpMethod.GET, path, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        won(alice, aliceEsn);

        // Naming a contact, or accusing, holds it; the author and the administrator are told.
        Map<String, Object> held = call(alice, HttpMethod.PUT, path, review("Mr Dupontel is a crook who never paid on time, avoid."), Map.class)
            .getBody();
        assertThat((Map<String, Object>) held.get("mine")).containsEntry("status", "HELD")
            .satisfies(mine -> assertThat((List<String>) mine.get("heldReasons")).containsExactlyInAnyOrder("CONTACT_NAME", "ACCUSATION"));
        assertThat(held).containsEntry("count", 0);
        assertThat(call(alice, HttpMethod.GET, "/network/notifications", null, List.class).getBody())
            .anySatisfy(notice -> assertThat((Map<String, Object>) notice).containsEntry("type", "REVIEW_HELD"));
        Long chiefUser = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, AdminIntegrationTest.CHIEF);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM network_notifications WHERE recipient_id = ? AND type = 'REVIEW_PENDING'",
            Integer.class, chiefUser)).isPositive();

        // The administrator publishes it edited out; Bob, at the same company by its SIREN, reads it without its author.
        List<Map<String, Object>> queue = (List<Map<String, Object>>) call(chief, HttpMethod.GET, "/admin/network/reports", null, Map.class)
            .getBody().get("heldReviews");
        Map<String, Object> waiting = queue.stream().filter(item -> "Capgemini".equals(item.get("companyName"))).findFirst().orElseThrow();
        assertThat((List<String>) waiting.get("reasons")).contains("CONTACT_NAME");
        long reviewId = ((Number) waiting.get("id")).longValue();
        assertThat(call(bob, HttpMethod.PUT, "/admin/network/held-reviews/" + reviewId, Map.of("publish", true), String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        call(chief, HttpMethod.PUT, "/admin/network/held-reviews/" + reviewId, Map.of("publish", true, "text", "Never paid on time, avoid."), Map.class);
        assertThat(call(alice, HttpMethod.GET, "/network/notifications", null, List.class).getBody())
            .anySatisfy(notice -> assertThat((Map<String, Object>) notice).containsEntry("type", "REVIEW_PUBLISHED"));
        Map<String, Object> seen = call(bob, HttpMethod.GET, "/network/reviews/client/" + bobEsn, null, Map.class).getBody();
        assertThat(seen).containsEntry("count", 1).containsEntry("paymentAverage", 2.0).containsEntry("canReview", false);
        assertThat((List<Map<String, Object>>) seen.get("reviews")).singleElement().satisfies(shown -> assertThat(shown)
            .containsEntry("text", "Never paid on time, avoid.").containsEntry("mine", false).doesNotContainKeys("authorId", "author"));

        // A fair review goes out at once, replacing the author's own.
        Map<String, Object> rewritten = call(alice, HttpMethod.PUT, path, review(fair), Map.class).getBody();
        assertThat((Map<String, Object>) rewritten.get("mine")).containsEntry("status", "PUBLISHED");
        assertThat((List<Map<String, Object>>) rewritten.get("reviews")).singleElement().satisfies(shown -> assertThat(shown)
            .containsEntry("text", fair).containsEntry("mine", true));
        Map<String, Object> phone = new HashMap<>(review("Call them at 06 12 34 56 78 before you sign anything, really."));
        assertThat((Map<String, Object>) call(alice, HttpMethod.PUT, path, phone, Map.class).getBody().get("mine"))
            .containsEntry("status", "HELD");
        call(chief, HttpMethod.PUT, "/admin/network/held-reviews/" + reviewId, Map.of("publish", false), Map.class);
        assertThat((Map<String, Object>) call(alice, HttpMethod.GET, path, null, Map.class).getBody().get("mine")).containsEntry("status", "REJECTED");
        call(alice, HttpMethod.PUT, path, review(fair), Map.class);

        // Reported by Bob, removed by the administrator: gone, and cannot be written again.
        assertThat(call(bob, HttpMethod.POST, "/network/reviews/" + reviewId + "/report", Map.of("reason", "MISLEADING"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.POST, "/network/reviews/" + reviewId + "/report", Map.of("reason", "SPAM"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // The dashboard counts it by reason and target, open since less than a day, under its company.
        Map<String, Object> dashboard = call(chief, HttpMethod.GET, "/admin/network/reports/dashboard?days=30", null, Map.class).getBody();
        assertThat(dashboard).containsEntry("days", 30);
        assertThat((Map<String, Object>) dashboard.get("byTarget")).containsKey("REVIEW");
        assertThat((Map<String, Object>) dashboard.get("byReason")).containsKey("MISLEADING");
        assertThat(((Number) dashboard.get("openUnderADay")).intValue()).isPositive();
        assertThat((List<Map<String, Object>>) dashboard.get("companies")).anySatisfy(company -> assertThat(company)
            .containsEntry("companyName", "Capgemini").containsEntry("open", 1).containsEntry("total", 1));
        assertThat(call(bob, HttpMethod.GET, "/admin/network/reports/dashboard", null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(chief, HttpMethod.GET, "/admin/network/reports/dashboard?days=0", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> report = ((List<Map<String, Object>>) call(chief, HttpMethod.GET, "/admin/network/reports", null, Map.class).getBody()
            .get("open")).stream().filter(open -> "REVIEW".equals(open.get("targetType"))).findFirst().orElseThrow();
        call(chief, HttpMethod.PUT, "/admin/network/reports/" + report.get("id"), Map.of("outcome", "REMOVED"), Map.class);
        assertThat(call(bob, HttpMethod.GET, "/network/reviews/client/" + bobEsn, null, Map.class).getBody()).containsEntry("count", 0);
        assertThat(call(alice, HttpMethod.PUT, path, review(fair), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.DELETE, path, null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private Account chief() {
        LoginRequest request = new LoginRequest();
        request.setEmail(AdminIntegrationTest.CHIEF);
        request.setPassword(PASSWORD);
        ResponseEntity<Map> signedIn = post("/auth/login", request);
        if (signedIn.getStatusCode() != HttpStatus.OK) {
            registerAs(AdminIntegrationTest.CHIEF);
            signedIn = post("/auth/login", request);
        }
        Map<?, ?> user = (Map<?, ?>) signedIn.getBody().get("user");
        Account chief = new Account((String) signedIn.getBody().get("token"), ((Number) user.get("id")).longValue());
        join(chief);
        return chief;
    }
}
