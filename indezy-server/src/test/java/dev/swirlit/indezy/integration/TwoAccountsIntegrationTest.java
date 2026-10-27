package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two real accounts, Alice (with a client) and Bob, registered through the API and calling it with their JWTs against
 * a running server, for the tests of what one workspace may see or change of another.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "indezy.security.permit-all=false")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
abstract class TwoAccountsIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected Account alice;
    protected Account bob;
    protected Long aliceClientId;

    protected record Account(String token, Long freelanceId) {
    }

    @BeforeEach
    void registerAccountsAndAliceData() {
        alice = register("alice");
        bob = register("bob");
        ResponseEntity<Map> created = call(alice, HttpMethod.POST, "/clients",
            Map.of("companyName", "Alice Corp", "city", "Paris", "isFinal", true, "freelanceId", alice.freelanceId()),
            Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        aliceClientId = ((Number) created.getBody().get("id")).longValue();
    }

    protected static long idOf(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    /** Posts the DTO rather than a map, whose {@code toString} the test client would log at DEBUG with the password. */
    protected Account register(String name) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName(name);
        request.setLastName("Test");
        request.setEmail(name + "." + UUID.randomUUID() + "@example.com");
        request.setPassword("Password123!");
        ResponseEntity<Map> response = restTemplate.postForEntity(url("/auth/register"), request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> user = (Map<?, ?>) response.getBody().get("user");
        return new Account((String) response.getBody().get("token"), ((Number) user.get("id")).longValue());
    }

    protected <T> ResponseEntity<T> call(Account account, HttpMethod method, String path, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(account.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), type);
    }

    protected <T> ResponseEntity<T> exchange(String path, HttpMethod method, HttpEntity<?> request, Class<T> type) {
        return restTemplate.exchange(url(path), method, request, type);
    }

    protected String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }
}
