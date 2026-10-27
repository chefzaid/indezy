package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.dto.PasswordChangeRequestDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice's sessions and activity log over HTTP, with Bob kept out of both. */
class AccountSecurityIntegrationTest extends TwoAccountsIntegrationTest {

    private static final String FIREFOX = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0";

    @Test
    @SuppressWarnings("unchecked")
    void listsAndEndsSessionsAndRecordsWhatHappened() {
        Account laptop = signIn(aliceEmail, PASSWORD);
        List<Map<String, Object>> sessions = call(laptop, HttpMethod.GET, "/users/sessions", null, List.class).getBody();
        assertThat(sessions).hasSize(2);
        assertThat(sessions.get(0)).containsEntry("current", true).containsEntry("method", "PASSWORD")
            .containsEntry("device", "Firefox on Windows");
        assertThat(sessions.get(1)).containsEntry("current", false).containsEntry("method", "REGISTRATION");
        long registration = ((Number) sessions.get(1).get("id")).longValue();

        // Bob cannot see or end Alice's sessions.
        assertThat(call(bob, HttpMethod.GET, "/users/sessions", null, List.class).getBody()).hasSize(1);
        assertThat(call(bob, HttpMethod.DELETE, "/users/sessions/" + registration, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, "/users/profile", null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Ended from the laptop, the registration's token stops working at once.
        assertThat(call(laptop, HttpMethod.DELETE, "/users/sessions/" + registration, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/users/profile", null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(laptop, HttpMethod.DELETE, "/users/sessions/" + registration, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // A wrong password is recorded; ending the other sessions signs the phone out.
        assertThat(post("/auth/login", login(aliceEmail, "Wrong-password1")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Account phone = signIn(aliceEmail, PASSWORD);
        assertThat(call(laptop, HttpMethod.POST, "/users/sessions/end-others", null, Map.class).getBody()).containsEntry("count", 1);
        assertThat(call(phone, HttpMethod.GET, "/users/profile", null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // A new password ends every other session too.
        Account tablet = signIn(aliceEmail, PASSWORD);
        PasswordChangeRequestDto change = new PasswordChangeRequestDto();
        change.setCurrentPassword(PASSWORD);
        change.setNewPassword("New-password-42");
        change.setConfirmPassword("New-password-42");
        assertThat(call(laptop, HttpMethod.POST, "/users/change-password", change, Boolean.class).getBody()).isTrue();
        assertThat(call(tablet, HttpMethod.GET, "/users/profile", null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(laptop, HttpMethod.GET, "/users/profile", null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> page = call(laptop, HttpMethod.GET, "/users/activity", null, Map.class).getBody();
        List<String> types = ((List<Map<String, Object>>) page.get("events")).stream().map(event -> (String) event.get("type")).toList();
        assertThat(types).containsExactly("OTHER_SESSIONS_REVOKED", "PASSWORD_CHANGED", "SIGNED_IN", "OTHER_SESSIONS_REVOKED",
            "SIGNED_IN", "SIGN_IN_REFUSED", "SESSION_REVOKED", "SIGNED_IN", "SIGNED_IN");
        assertThat(((List<Map<String, Object>>) page.get("events")).get(5)).containsEntry("detail", "PASSWORD");
        assertThat(call(bob, HttpMethod.GET, "/users/activity", null, String.class).getBody()).doesNotContain("SIGN_IN_REFUSED");

        // Signing out ends the laptop's own session.
        assertThat(call(laptop, HttpMethod.DELETE, "/users/sessions/current", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(laptop, HttpMethod.GET, "/users/profile", null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Account again = signIn(aliceEmail, "New-password-42");
        assertThat(call(again, HttpMethod.GET, "/users/activity", null, String.class).getBody()).contains("SIGNED_OUT");
    }

    private Account signIn(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, FIREFOX);
        ResponseEntity<Map> response = exchange("/auth/login", HttpMethod.POST, new HttpEntity<>(login(email, password), headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new Account((String) response.getBody().get("token"), alice.freelanceId());
    }

    private static LoginRequest login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
