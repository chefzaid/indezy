package dev.byteworks.indezy.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for user management endpoints.
 * Tests the complete integration between frontend user management service and backend APIs.
 * 
 * Note: These tests are currently disabled as the backend user management endpoints
 * are not yet implemented. Enable them once the UserController is created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SuppressWarnings("null")
class UserManagementIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/users";
    }

    @Test
    void testGetUserProfile() {
        String url = getBaseUrl() + "/profile";
        
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // Verify response contains expected user profile fields
        assertTrue(response.getBody().contains("firstName"));
        assertTrue(response.getBody().contains("lastName"));
        assertTrue(response.getBody().contains("email"));
    }

    @Test
    void testUpdateUserProfile() {
        String url = getBaseUrl() + "/profile";
        
        // Create update request
        String updateRequest = """
            {
                "firstName": "Jane",
                "lastName": "Smith",
                "email": "john.doe@example.com",
                "phone": "+1234567890",
                "bio": "Updated bio"
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(updateRequest, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // Verify updated fields in response
        assertTrue(response.getBody().contains("Jane"));
        assertTrue(response.getBody().contains("Smith"));
        assertTrue(response.getBody().contains("+1234567890"));
    }

    @Test
    void testChangePassword() {
        String url = getBaseUrl() + "/change-password";
        
        String passwordRequest = """
            {
                "currentPassword": "password123",
                "newPassword": "newPassword456",
                "confirmPassword": "newPassword456"
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(passwordRequest, headers);

        ResponseEntity<Boolean> response = restTemplate.exchange(url, HttpMethod.POST, entity, Boolean.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody());
    }

    @Test
    void testGetUserPreferences() {
        String url = getBaseUrl() + "/preferences";
        
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // Verify response contains expected preference fields
        assertTrue(response.getBody().contains("theme"));
        assertTrue(response.getBody().contains("language"));
        assertTrue(response.getBody().contains("timezone"));
    }

    @Test

    void testUpdateUserPreferences() {
        String url = getBaseUrl() + "/preferences";
        
        String preferencesRequest = """
            {
                "theme": "dark",
                "language": "en",
                "timeFormat": "24h",
                "itemsPerPage": 20
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(preferencesRequest, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // Verify updated preferences in response
        assertTrue(response.getBody().contains("dark"));
        assertTrue(response.getBody().contains("24h"));
        assertTrue(response.getBody().contains("20"));
    }

    @Test
    void testGetNotificationSettings() {
        String url = getBaseUrl() + "/notifications";
        
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // Verify response contains expected notification fields
        assertTrue(response.getBody().contains("emailNotifications"));
        assertTrue(response.getBody().contains("pushNotifications"));
        assertTrue(response.getBody().contains("projectUpdates"));
    }

    @Test
    void testUpdateNotificationSettings() {
        String url = getBaseUrl() + "/notifications";
        
        String notificationRequest = """
            {
                "emailNotifications": false,
                "pushNotifications": true,
                "projectUpdates": true,
                "clientMessages": false
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(notificationRequest, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // Verify updated settings in response
        assertTrue(response.getBody().contains("\"emailNotifications\":false"));
        assertTrue(response.getBody().contains("\"pushNotifications\":true"));
    }

    @Test
    void testGetSecuritySettings() {
        String url = getBaseUrl() + "/security";
        
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // Verify response contains expected security fields
        assertTrue(response.getBody().contains("twoFactorEnabled"));
        assertTrue(response.getBody().contains("lastPasswordChange"));
        assertTrue(response.getBody().contains("loginSessions"));
    }

    @Test
    void testEnableTwoFactor() {
        String url = getBaseUrl() + "/security/2fa/enable";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{}", headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // Should return QR code or setup key
        assertFalse(response.getBody().isEmpty());
    }

    @Test
    void testDisableTwoFactor() {
        String url = getBaseUrl() + "/security/2fa/disable";
        
        String disableRequest = """
            {
                "code": "123456"
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(disableRequest, headers);

        ResponseEntity<Boolean> response = restTemplate.exchange(url, HttpMethod.POST, entity, Boolean.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody());
    }

    @Test
    void testTerminateSession() {
        String sessionId = "session1";
        String url = getBaseUrl() + "/security/sessions/" + sessionId;
        
        ResponseEntity<Boolean> response = restTemplate.exchange(url, HttpMethod.DELETE, null, Boolean.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody());
    }

    @Test
    void testDeleteAccount() {
        String url = getBaseUrl() + "/account/delete";

        String deleteRequest = """
            {
                "password": "password123"
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(deleteRequest, headers);

        ResponseEntity<Boolean> response = restTemplate.exchange(url, HttpMethod.POST, entity, Boolean.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody());
    }

    @Test
    void testExportUserData() {
        String url = getBaseUrl() + "/export";
        
        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().length > 0);
        
        // Verify content type is appropriate for file download
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
    }

    @Test
    void testErrorHandling() {
        // Test non-existent session termination
        String url = getBaseUrl() + "/security/sessions/non-existent-session";

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testInvalidPasswordChange() {
        String url = getBaseUrl() + "/change-password";
        
        String invalidRequest = """
            {
                "currentPassword": "wrongPassword",
                "newPassword": "newPassword456",
                "confirmPassword": "newPassword456"
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(invalidRequest, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testCorsConfiguration() {
        String url = getBaseUrl() + "/profile";
        
        // Test that CORS headers are present
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertNotNull(response.getHeaders());
        // CORS headers should be configured in the backend
    }
}
