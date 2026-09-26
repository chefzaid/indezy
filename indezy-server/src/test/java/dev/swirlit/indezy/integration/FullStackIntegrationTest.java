package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the full stack application.
 * Tests the complete integration between indezy-web services and indezy-server APIs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FullStackIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testFreelanceEndpoints() {
        String baseUrl = "http://localhost:" + port + "/api/freelances";
        
        // Test GET all freelances
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("John"));
        assertTrue(response.getBody().contains("Doe"));

        // Test GET freelance by ID
        ResponseEntity<String> freelanceResponse = restTemplate.getForEntity(baseUrl + "/1", String.class);
        assertEquals(HttpStatus.OK, freelanceResponse.getStatusCode());
        assertNotNull(freelanceResponse.getBody());
        assertTrue(freelanceResponse.getBody().contains("\"id\":1"));
        assertTrue(freelanceResponse.getBody().contains("John"));
    }

    @Test
    void testClientEndpoints() {
        String baseUrl = "http://localhost:" + port + "/api/clients";
        
        // Test GET all clients
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Test Company"));

        // Test GET client by ID
        ResponseEntity<String> clientResponse = restTemplate.getForEntity(baseUrl + "/1", String.class);
        assertEquals(HttpStatus.OK, clientResponse.getStatusCode());
        assertNotNull(clientResponse.getBody());
        assertTrue(clientResponse.getBody().contains("\"id\":1"));
        assertTrue(clientResponse.getBody().contains("Test Company"));
    }

    @Test
    void testContactEndpoints() {
        String baseUrl = "http://localhost:" + port + "/api/contacts";
        
        // Test GET all contacts
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Jane"));
        assertTrue(response.getBody().contains("Smith"));

        // Test GET contact by ID
        ResponseEntity<String> contactResponse = restTemplate.getForEntity(baseUrl + "/1", String.class);
        assertEquals(HttpStatus.OK, contactResponse.getStatusCode());
        assertNotNull(contactResponse.getBody());
        assertTrue(contactResponse.getBody().contains("\"id\":1"));
        assertTrue(contactResponse.getBody().contains("Jane"));
    }

    @Test
    void testProjectEndpoints() {
        String baseUrl = "http://localhost:" + port + "/api/projects";
        
        // Test GET all projects
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Senior Full Stack Developer"));

        // Test GET project by ID
        ResponseEntity<String> projectResponse = restTemplate.getForEntity(baseUrl + "/1", String.class);
        assertEquals(HttpStatus.OK, projectResponse.getStatusCode());
        assertNotNull(projectResponse.getBody());
        assertTrue(projectResponse.getBody().contains("\"id\":1"));
        assertTrue(projectResponse.getBody().contains("Senior Full Stack Developer"));
    }

    @Test
    void testSourceEndpoints() {
        String baseUrl = "http://localhost:" + port + "/api/sources";
        
        // Test GET all sources
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("LinkedIn"));

        // Test GET source by ID
        ResponseEntity<String> sourceResponse = restTemplate.getForEntity(baseUrl + "/1", String.class);
        assertEquals(HttpStatus.OK, sourceResponse.getStatusCode());
        assertNotNull(sourceResponse.getBody());
        assertTrue(sourceResponse.getBody().contains("\"id\":1"));
        assertTrue(sourceResponse.getBody().contains("LinkedIn"));
    }

    @Test
    void testInterviewStepEndpoints() {
        String baseUrl = "http://localhost:" + port + "/api/interview-steps/by-project/1/ordered";

        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Phone Screening"));
        assertTrue(response.getBody().contains("VALIDATED"));
    }

    @Test
    void testErrorHandling() {
        String baseUrl = "http://localhost:" + port + "/api/freelances";
        
        // Test GET non-existent freelance
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/999", String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
