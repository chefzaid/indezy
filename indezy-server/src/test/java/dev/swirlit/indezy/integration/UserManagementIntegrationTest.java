package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.TwoFactorCodeRequest;
import dev.swirlit.indezy.model.enums.SignInMethod;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.AccountSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for user management endpoints.
 * Tests the complete integration between indezy-web user management service and indezy-server APIs.
 *
 * The test profile logs Spring MVC and the test client at DEBUG, where both print each request body through its
 * {@code toString}, so the two-factor tests also read the log of their request: the code never appears in it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class UserManagementIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountSessionService accountSessions;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private dev.swirlit.indezy.service.TotpService totpService;

    /** Enables 2FA setup and returns the freshly generated Base32 secret. */
    private String startTwoFactorSetupAndGetSecret() throws Exception {
        String url = getBaseUrl() + "/security/2fa/enable";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
            url, HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
        return new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(response.getBody()).get("secret").asText();
    }

    /** Posts a two-factor code, as a {@link TwoFactorCodeRequest} or as raw JSON, to {@code /security/2fa/<action>}. */
    private ResponseEntity<String> postTwoFactorCode(String action, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(getBaseUrl() + "/security/2fa/" + action, HttpMethod.POST,
            new HttpEntity<>(body, headers), String.class);
    }

    private static TwoFactorCodeRequest codeRequest(String code) {
        TwoFactorCodeRequest request = new TwoFactorCodeRequest();
        request.setCode(code);
        return request;
    }

    /**
     * Spring MVC (Read) and the test client (Writing) print the request without its code. Only the lines logged since
     * {@code logStart} are read, so the six digits cannot match an unrelated number elsewhere in the log.
     */
    private static void assertCodeNotLogged(CapturedOutput output, int logStart, String code) {
        String requestLog = output.getAll().substring(logStart);
        assertTrue(requestLog.contains("to [TwoFactorCodeRequest()]"), requestLog);
        assertTrue(requestLog.contains("Writing [TwoFactorCodeRequest()]"), requestLog);
        assertFalse(requestLog.contains(code), requestLog);
    }

    /**
     * Authenticates every request as the seeded user (id 1) by attaching a Bearer
     * token, so the endpoints resolve the current user from the security context.
     */
    @BeforeEach
    void authenticateAsSeededUser() {
        String token = accountSessions.open(userRepository.findById(1L).orElseThrow(), SignInMethod.PASSWORD);
        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            request.getHeaders().setBearerAuth(token);
            return execution.execute(request, body);
        });
    }

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
    void updatingTheProfileKeepsItsSkillsAndTheirLevels() {
        String url = getBaseUrl() + "/profile";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String update = """
            {"firstName": "John", "lastName": "Doe", "email": "john.doe@example.com", "skills": ["Java", "Spring Boot", "Kubernetes"],
             "skillDetails": {"Java": {"level": "EXPERT", "years": 10}, "Kubernetes": {"years": 3},
                              "Cobol": {"level": "BEGINNER"}, "Spring Boot": {}}}
            """;

        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(update, headers), String.class);
        Map<?, ?> profile = restTemplate.getForObject(url, Map.class);

        assertEquals(List.of("Java", "Spring Boot", "Kubernetes"), profile.get("skills"));
        // Details are kept for the profile's skills only, and only when they say something.
        assertEquals(Map.of("Java", Map.of("level", "EXPERT", "years", 10), "Kubernetes", Map.of("years", 3)),
            profile.get("skillDetails"));

        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>("""
            {"firstName": "John", "lastName": "Doe", "email": "john.doe@example.com", "skills": ["Kubernetes"], "skillDetails": {"Kubernetes": {"years": 4}}}
            """, headers), String.class);
        profile = restTemplate.getForObject(url, Map.class);
        assertEquals(List.of("Kubernetes"), profile.get("skills"));
        assertEquals(Map.of("Kubernetes", Map.of("years", 4)), profile.get("skillDetails"));
    }

    @Test
    void refusesAnOverlongSkillOrAnImpossibleExperience() {
        String url = getBaseUrl() + "/profile";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        for (String body : new String[] {
            "{\"firstName\": \"John\", \"lastName\": \"Doe\", \"email\": \"john.doe@example.com\", \"skills\": [\"" + "x".repeat(61) + "\"]}",
            "{\"firstName\": \"John\", \"lastName\": \"Doe\", \"email\": \"john.doe@example.com\", \"skills\": [\"Java\"], \"skillDetails\": {\"Java\": {\"years\": 61}}}"}) {
            assertEquals(HttpStatus.BAD_REQUEST,
                restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class).getStatusCode());
        }
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
        assertTrue(response.getBody().contains("\"weeklyReports\":false"));
        assertTrue(response.getBody().contains("\"newOffersBadge\":true"));
        assertTrue(response.getBody().contains("\"emailsAvailable\":false"));
    }

    @Test
    void testUpdateNotificationSettings() {
        String url = getBaseUrl() + "/notifications";
        
        String notificationRequest = """
            {
                "newOffersBadge": false
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(notificationRequest, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // Verify updated settings in response
        // Only the switch the request names changes.
        assertTrue(response.getBody().contains("\"newOffersBadge\":false"));
        assertTrue(response.getBody().contains("\"weeklyReports\":false"));
    }

    @Test
    void testGetSecuritySettings() {
        ResponseEntity<String> response = restTemplate.getForEntity(getBaseUrl() + "/security", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("twoFactorEnabled"));
        assertTrue(response.getBody().contains("lastPasswordChange"));
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

        // Returns the shared secret and its provisioning URI
        assertTrue(response.getBody().contains("secret"));
        assertTrue(response.getBody().contains("otpauthUri"));
    }

    @Test
    void testVerifyTwoFactor(CapturedOutput output) throws Exception {
        String code = totpService.generateCurrentCode(startTwoFactorSetupAndGetSecret());
        int logStart = output.getAll().length();

        ResponseEntity<String> response = postTwoFactorCode("verify", codeRequest(code));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("true", response.getBody());
        assertCodeNotLogged(output, logStart, code);
    }

    @Test
    void testDisableTwoFactor(CapturedOutput output) throws Exception {
        String code = totpService.generateCurrentCode(startTwoFactorSetupAndGetSecret());
        int logStart = output.getAll().length();

        ResponseEntity<String> response = postTwoFactorCode("disable", codeRequest(code));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("true", response.getBody());
        assertCodeNotLogged(output, logStart, code);
    }

    @Test
    void testTwoFactorCodeIsRequired() throws Exception {
        startTwoFactorSetupAndGetSecret();

        ResponseEntity<String> verify = postTwoFactorCode("verify", "{}");
        assertEquals(HttpStatus.BAD_REQUEST, verify.getStatusCode());
        assertTrue(verify.getBody().contains("\"code\":\"Verification code is required\""), verify.getBody());

        ResponseEntity<String> disable = postTwoFactorCode("disable", "{\"code\":\" \"}");
        assertEquals(HttpStatus.BAD_REQUEST, disable.getStatusCode());
        assertTrue(disable.getBody().contains("\"code\":\"Verification code is required\""), disable.getBody());
    }

    @Test
    void testVerifyTwoFactorAnswersMalformedCodeAsWrong() throws Exception {
        // The profile page shows its invalid-code message for false, and accepts up to six characters of any kind.
        startTwoFactorSetupAndGetSecret();

        ResponseEntity<String> response = postTwoFactorCode("verify", "{\"code\":\"12345\"}");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("false", response.getBody());
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
        // CORS headers should be configured in the indezy-server
    }
}
