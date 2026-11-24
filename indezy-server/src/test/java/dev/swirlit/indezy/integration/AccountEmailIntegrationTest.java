package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.ResendMailer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Accounts with a password in production, Resend replaced: registering sends a confirmation link and opens nothing,
 * signing in waits for it, the link signs in; a forgotten password gets a link that sets a new one and ends the other
 * sessions; asking for an unknown address answers the same.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"indezy.security.permit-all=false", "indezy.auth.email-verification=true", "indezy.mail.resend-api-key=re_test"})
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class AccountEmailIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9%_-]+)");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ResendMailer mailer;

    private ResponseEntity<Map> post(String path, Object body) {
        return rest.postForEntity("http://localhost:" + port + "/api" + path, body, Map.class);
    }

    private String sentToken() throws Exception {
        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer).send(sent.capture());
        Matcher match = TOKEN.matcher(sent.getValue().text());
        assertThat(match.find()).isTrue();
        clearInvocations(mailer);
        return URLDecoder.decode(match.group(1), StandardCharsets.UTF_8);
    }

    @Test
    void confirmsTheEmailBeforeSigningInAndResetsAForgottenPassword() throws Exception {
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-1");
        String email = "camille." + UUID.randomUUID() + "@example.com";
        Map<String, Object> registration = Map.of("firstName", "Camille", "lastName", "Test", "email", email, "password", "Password123!");

        ResponseEntity<Map> registered = post("/auth/register", registration);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registered.getBody()).containsEntry("emailVerificationRequired", true).containsEntry("token", null);
        String verification = sentToken();
        // Signing in waits for the link; asking again within two minutes sends nothing more.
        ResponseEntity<Map> early = post("/auth/login", Map.of("email", email, "password", "Password123!"));
        assertThat(early.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(early.getBody()).containsEntry("emailVerificationRequired", true);
        assertThat(post("/auth/verify-email/resend", Map.of("email", email)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(mailer, never()).send(any());
        assertThat(post("/auth/verify-email", Map.of("token", "wrong")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> verified = post("/auth/verify-email", Map.of("token", verification));
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        String session = (String) verified.getBody().get("token");
        assertThat(session).isNotBlank();
        assertThat(post("/auth/verify-email", Map.of("token", verification)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/auth/login", Map.of("email", email, "password", "Password123!")).getStatusCode()).isEqualTo(HttpStatus.OK);

        // A forgotten password: an unknown address answers the same, and sends nothing.
        assertThat(post("/auth/password/forgot", Map.of("email", "nobody." + UUID.randomUUID() + "@example.com")).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        verify(mailer, never()).send(any());
        jdbc.update("UPDATE users SET account_email_sent_at = NULL WHERE email = ?", email);
        assertThat(post("/auth/password/forgot", Map.of("email", email)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String reset = sentToken();
        assertThat(post("/auth/password/reset", Map.of("token", reset, "password", "short")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Map> renewed = post("/auth/password/reset", Map.of("token", reset, "password", "NewPassword456!"));
        assertThat(renewed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) renewed.getBody().get("token")).isNotBlank();
        assertThat(post("/auth/password/reset", Map.of("token", reset, "password", "NewPassword456!")).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/auth/login", Map.of("email", email, "password", "Password123!")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/auth/login", Map.of("email", email, "password", "NewPassword456!")).getStatusCode()).isEqualTo(HttpStatus.OK);
        // The session opened before the reset ended with it.
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session);
        assertThat(rest.exchange("http://localhost:" + port + "/api/users/profile", HttpMethod.GET, new HttpEntity<>(headers), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // An expired link works no more.
        jdbc.update("UPDATE users SET account_email_sent_at = NULL WHERE email = ?", email);
        post("/auth/password/forgot", Map.of("email", email));
        String late = sentToken();
        jdbc.update("UPDATE users SET password_reset_expires_at = ? WHERE email = ?",
            java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), email);
        assertThat(post("/auth/password/reset", Map.of("token", late, "password", "Another789!")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
