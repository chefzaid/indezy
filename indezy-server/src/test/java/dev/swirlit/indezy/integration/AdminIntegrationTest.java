package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The admin console, for the administrators the configuration names only. */
@TestPropertySource(properties = "indezy.admin.emails=" + AdminIntegrationTest.CHIEF)
class AdminIntegrationTest extends TwoAccountsIntegrationTest {

    static final String CHIEF = "chief.admin@example.com";

    @Test
    @SuppressWarnings("unchecked")
    void letsAnAdministratorWatchUsageDisableAccountsAndTurnFeaturesOff() {
        assertThat(call(bob, HttpMethod.GET, "/admin/overview", null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(bob, HttpMethod.PUT, "/admin/features/DATA_IMPORT", Map.of("enabled", false), String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(bob, HttpMethod.GET, "/users/capabilities", null, Map.class).getBody()).containsEntry("admin", false)
            .containsEntry("features", Map.of("OFFER_FEED", true, "PUBLIC_PROFILES", true, "DATA_IMPORT", true, "YEARLY_REVIEW", true));

        Account chief = chief();
        assertThat(call(chief, HttpMethod.GET, "/users/capabilities", null, Map.class).getBody()).containsEntry("admin", true);
        Map<String, Object> overview = call(chief, HttpMethod.GET, "/admin/overview", null, Map.class).getBody();
        assertThat(((Number) overview.get("accounts")).longValue()).isGreaterThanOrEqualTo(3);
        assertThat((Map<String, Object>) overview.get("featureWorkspaces")).containsKey("opportunities").doesNotContainKey("accounts");

        Map<String, Object> found = call(chief, HttpMethod.GET, "/admin/accounts?query=BOB", null, Map.class).getBody();
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) found.get("accounts");
        assertThat(accounts).isNotEmpty().allSatisfy(account -> assertThat(account.get("email").toString()).startsWith("bob."));
        long bobId = ((Number) accounts.stream().filter(account -> bobEmail.equals(account.get("email"))).findFirst()
            .orElseThrow().get("id")).longValue();

        // Disabled, Bob's sessions end and he cannot sign in again; enabled, he signs in again.
        assertThat(call(chief, HttpMethod.POST, "/admin/accounts/" + bobId + "/disable", null, Map.class).getBody().get("disabledAt"))
            .isNotNull();
        assertThat(call(bob, HttpMethod.GET, "/users/profile", null, String.class).getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        assertThat(login(bobEmail).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        call(chief, HttpMethod.POST, "/admin/accounts/" + bobId + "/enable", null, Map.class);
        assertThat(call(bob, HttpMethod.GET, "/users/profile", null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<Map> again = login(bobEmail);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        bob = new Account((String) again.getBody().get("token"), bob.freelanceId());
        assertThat(call(bob, HttpMethod.GET, "/users/profile", null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(bob, HttpMethod.GET, "/users/activity", null, String.class).getBody())
            .contains("ACCOUNT_DISABLED", "ACCOUNT_ENABLED");

        long chiefId = ((Number) ((List<Map<String, Object>>) call(chief, HttpMethod.GET, "/admin/accounts?query=chief.admin", null,
            Map.class).getBody().get("accounts")).get(0).get("id")).longValue();
        assertThat(call(chief, HttpMethod.POST, "/admin/accounts/" + chiefId + "/disable", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // A feature turned off answers 403 until it is on again.
        List<Map<String, Object>> features = call(chief, HttpMethod.PUT, "/admin/features/PUBLIC_PROFILES", Map.of("enabled", false),
            List.class).getBody();
        assertThat(features).contains(Map.of("flag", "PUBLIC_PROFILES", "enabled", false));
        try {
            ResponseEntity<Map> refused = call(bob, HttpMethod.GET, "/users/public-profile", null, Map.class);
            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(refused.getBody()).containsEntry("message", "This feature is turned off");
            assertThat(exchange("/public/profiles/anyone", HttpMethod.GET, null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call(bob, HttpMethod.GET, "/users/capabilities", null, Map.class).getBody().get("features").toString())
                .contains("PUBLIC_PROFILES=false");
            assertThat(call(bob, HttpMethod.GET, "/users/profile", null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            call(chief, HttpMethod.PUT, "/admin/features/PUBLIC_PROFILES", Map.of("enabled", true), List.class);
        }
        assertThat(call(bob, HttpMethod.GET, "/users/public-profile", null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** The configured administrator, registered once for the whole database. */
    private Account chief() {
        ResponseEntity<Map> signedIn = login(CHIEF);
        if (signedIn.getStatusCode() != HttpStatus.OK) {
            registerAs(CHIEF);
            signedIn = login(CHIEF);
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
