package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two real accounts (registered through the API, authenticated with their JWTs) must never see
 * or change each other's workspace.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "indezy.security.permit-all=false")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class WorkspaceIsolationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private Account alice;
    private Account bob;
    private Long aliceClientId;

    private record Account(String token, Long freelanceId) {
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

    @Test
    void anotherAccountCannotReadOrChangeTheResource() {
        assertThat(call(bob, HttpMethod.GET, "/clients/" + aliceClientId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.PUT, "/clients/" + aliceClientId,
            Map.of("companyName", "Hijacked", "city", "Paris", "isFinal", true, "freelanceId", bob.freelanceId()),
            String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, "/clients/" + aliceClientId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, "/clients/" + aliceClientId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }

    @Test
    void anotherAccountCannotAddressTheWorkspaceOrReferenceItsRecords() {
        assertThat(call(bob, HttpMethod.GET, "/projects/by-freelance/" + alice.freelanceId(), null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(bob, HttpMethod.GET, "/projects/stats/dashboard/" + alice.freelanceId(), null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Bob cannot attach a project of his own to Alice's client.
        assertThat(call(bob, HttpMethod.POST, "/projects",
            Map.of("role", "Dev", "dailyRate", 500, "clientId", aliceClientId, "freelanceId", bob.freelanceId()),
            String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Nor create records inside Alice's workspace.
        assertThat(call(bob, HttpMethod.POST, "/clients",
            Map.of("companyName", "Intruder", "city", "Lyon", "isFinal", true, "freelanceId", alice.freelanceId()),
            String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listEndpointsOnlyReturnTheCallersRecords() {
        ResponseEntity<List> bobClients = call(bob, HttpMethod.GET, "/clients", null, List.class);
        assertThat(bobClients.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bobClients.getBody()).isEmpty();

        ResponseEntity<List> aliceClients = call(alice, HttpMethod.GET, "/clients", null, List.class);
        assertThat(aliceClients.getBody()).hasSize(1);

        ResponseEntity<List> bobFreelances = call(bob, HttpMethod.GET, "/freelances", null, List.class);
        assertThat(bobFreelances.getBody()).hasSize(1);
    }

    private Account register(String name) {
        String email = name + "." + UUID.randomUUID() + "@example.com";
        ResponseEntity<Map> response = restTemplate.postForEntity(url("/auth/register"),
            Map.of("firstName", name, "lastName", "Test", "email", email, "password", "Password123!"),
            Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> user = (Map<?, ?>) response.getBody().get("user");
        return new Account((String) response.getBody().get("token"), ((Number) user.get("id")).longValue());
    }

    private <T> ResponseEntity<T> call(Account account, HttpMethod method, String path, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(account.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), type);
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }
}
