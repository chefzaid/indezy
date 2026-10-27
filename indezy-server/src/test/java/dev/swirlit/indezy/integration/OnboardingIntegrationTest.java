package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A new account's welcome steps: pending from registration until finished or skipped, with a starter process saved. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "indezy.security.permit-all=false")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class OnboardingIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @SuppressWarnings("unchecked")
    void aNewAccountIsWelcomedUntilItFinishesAndSavesAStarterProcessOfItsOwn() {
        String email = "new." + UUID.randomUUID() + "@example.com";
        ResponseEntity<Map> registered = restTemplate.postForEntity(url("/auth/register"), register(email), Map.class);
        Map<String, Object> user = (Map<String, Object>) registered.getBody().get("user");
        assertThat(user).containsEntry("onboardingPending", true);
        String token = (String) registered.getBody().get("token");
        long freelanceId = ((Number) user.get("id")).longValue();
        assertThat(login(email).get("onboardingPending")).isEqualTo(true);

        Map<String, Object> starter = Map.of("name", "Through an ESN", "steps",
            List.of(Map.of("title", "Call with the ESN", "checklist", List.of("Rate asked")), Map.of("title", "Client interview")));
        assertThat(call(token, HttpMethod.POST, "/pipeline-templates/steps", starter, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);
        assertThat(call(token, HttpMethod.GET, "/pipeline-templates", null, List.class).getBody()).hasSize(1);

        // Another account cannot write a template into this workspace.
        String other = (String) restTemplate.postForEntity(url("/auth/register"), register("other." + UUID.randomUUID() + "@example.com"),
            Map.class).getBody().get("token");
        Map<String, Object> intrusion = Map.of("name", "Intruder", "freelanceId", freelanceId, "steps", List.of(Map.of("title", "Call")));
        assertThat(call(other, HttpMethod.POST, "/pipeline-templates/steps", intrusion, String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(call(token, HttpMethod.POST, "/users/onboarding/complete", null, Void.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(login(email).get("onboardingPending")).isEqualTo(false);
        assertThat(restTemplate.postForEntity(url("/users/onboarding/complete"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anAccountOlderThanTheWelcomeStepsIsNotSentToThem() {
        String email = "old." + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity(url("/auth/register"), register(email), Map.class);
        // As Hibernate leaves the column of the accounts it adds it to.
        jdbc.update("update users set onboarding_pending = null where email = ?", email);

        assertThat(login(email).get("onboardingPending")).isEqualTo(false);
    }

    private static RegisterRequest register(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("New");
        request.setLastName("Freelancer");
        request.setEmail(email);
        request.setPassword(PASSWORD);
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> login(String email) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        ResponseEntity<Map> response = restTemplate.postForEntity(url("/auth/login"), request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) response.getBody().get("user");
    }

    private <T> ResponseEntity<T> call(String token, HttpMethod method, String path, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), type);
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }
}
