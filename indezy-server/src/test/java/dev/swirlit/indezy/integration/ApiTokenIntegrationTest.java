package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice's personal API token reads her workspace, and nothing else. */
class ApiTokenIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void readsTheWorkspaceOnlyUntilRevoked() {
        Map<String, Object> created = call(alice, HttpMethod.POST, "/users/api-tokens", Map.of("name", " Zapier ", "expiresInDays", 90),
            Map.class).getBody();
        String secret = (String) created.get("secret");
        Map<String, Object> token = (Map<String, Object>) created.get("token");
        assertThat(secret).startsWith("idz_").hasSize(47);
        assertThat(token).containsEntry("name", "Zapier").containsEntry("tokenStart", secret.substring(0, 12));
        assertThat(token.get("expiresAt")).isNotNull();

        // Listed without the token; stored only as its hash.
        String listed = call(alice, HttpMethod.GET, "/users/api-tokens", null, String.class).getBody();
        assertThat(listed).contains("Zapier").doesNotContain(secret);
        assertThat(jdbcTemplate.queryForList("SELECT token_hash FROM api_tokens", String.class)).doesNotContain(secret);
        assertThat(call(bob, HttpMethod.GET, "/users/api-tokens", null, List.class).getBody()).isEmpty();

        // It reads Alice's workspace, never Bob's.
        assertThat(withToken(secret, HttpMethod.GET, "/clients/by-freelance/" + alice.freelanceId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withToken(secret, HttpMethod.GET, "/clients/" + aliceClientId).getBody()).contains("\"id\":" + aliceClientId);
        assertThat(withToken(secret, HttpMethod.GET, "/clients/by-freelance/" + bob.freelanceId()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Read-only, and kept out of the account and the admin console.
        assertThat(withToken(secret, HttpMethod.DELETE, "/clients/" + aliceClientId).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withToken(secret, HttpMethod.GET, "/users/profile").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withToken(secret, HttpMethod.GET, "/users/api-tokens").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withToken(secret, HttpMethod.GET, "/admin/overview").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(alice, HttpMethod.GET, "/clients/" + aliceClientId, null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withToken(secret + "x", HttpMethod.GET, "/clients/" + aliceClientId).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Bob cannot revoke it; Alice can, and it stops working.
        long id = ((Number) token.get("id")).longValue();
        assertThat(call(bob, HttpMethod.DELETE, "/users/api-tokens/" + id, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.DELETE, "/users/api-tokens/" + id, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(withToken(secret, HttpMethod.GET, "/clients/" + aliceClientId).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(alice, HttpMethod.GET, "/users/activity", null, String.class).getBody())
            .contains("API_TOKEN_CREATED", "API_TOKEN_REVOKED");

        // A lifetime other than 30, 90 or 365 days, a blank name or an eleventh token is refused.
        assertThat(call(alice, HttpMethod.POST, "/users/api-tokens", Map.of("name", "Odd", "expiresInDays", 7), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.POST, "/users/api-tokens", Map.of("name", " "), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        for (int i = 0; i < 10; i++) {
            assertThat(call(alice, HttpMethod.POST, "/users/api-tokens", Map.of("name", "Token " + i), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        }
        assertThat(call(alice, HttpMethod.POST, "/users/api-tokens", Map.of("name", "One too many"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsOnlyTheScopesItWasGiven() {
        Map<String, Object> created = call(alice, HttpMethod.POST, "/users/api-tokens", Map.of("name", "Accounting", "expiresInDays", 30,
            "scopes", List.of("INVOICES", "MISSIONS")), Map.class).getBody();
        String secret = (String) created.get("secret");
        assertThat((List<String>) ((Map<String, Object>) created.get("token")).get("scopes")).containsExactly("MISSIONS", "INVOICES");
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Architecte", "dailyRate", 650, "status", "WON",
            "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class));

        assertThat(withToken(secret, HttpMethod.GET, "/invoices/by-freelance/" + alice.freelanceId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withToken(secret, HttpMethod.GET, "/projects/" + project + "/activity-reports").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withToken(secret, HttpMethod.GET, "/projects/" + project + "/purchase-orders").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withToken(secret, HttpMethod.GET, "/projects/" + project).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withToken(secret, HttpMethod.GET, "/clients/" + aliceClientId).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(withToken(secret, HttpMethod.GET, "/search?q=arch").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Without scopes, it reads the whole workspace, the search included.
        String full = (String) call(alice, HttpMethod.POST, "/users/api-tokens", Map.of("name", "Everything"), Map.class).getBody().get("secret");
        assertThat(withToken(full, HttpMethod.GET, "/search?q=arch").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withToken(full, HttpMethod.GET, "/projects/" + project).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(alice, HttpMethod.POST, "/users/api-tokens", Map.of("name", "Odd", "scopes", List.of("NOT_A_SCOPE")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<String> withToken(String secret, HttpMethod method, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secret);
        return exchange(path, method, new HttpEntity<>(headers), String.class);
    }
}
