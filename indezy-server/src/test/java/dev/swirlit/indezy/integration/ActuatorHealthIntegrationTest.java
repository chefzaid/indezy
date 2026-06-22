package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Spring Boot Actuator health endpoint that Docker and Kubernetes
 * probes rely on ({@code /api/actuator/health}, plus the liveness/readiness groups).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class ActuatorHealthIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/health"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"status\":\"UP\""),
                "Expected health status UP but was: " + response.getBody());
    }

    @Test
    void livenessProbeIsAvailable() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/health/liveness"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"status\":\"UP\""));
    }

    @Test
    void readinessProbeIsAvailable() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/actuator/health/readiness"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"status\":\"UP\""));
    }
}
