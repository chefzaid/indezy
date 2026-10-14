package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.dto.PasswordChangeRequestDto;
import dev.swirlit.indezy.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BCrypt cannot hash more than 72 bytes, so registration and the password change answer a validation error for a longer
 * password, counted in UTF-8 bytes, before it reaches BCrypt: a new email used to get a 409 as if it already had an
 * account. The password change also enforces the 8-character minimum of the profile form, which a request sent outside
 * the form used to bypass, and its matching confirmation. The DTOs are posted rather than maps, whose {@code toString}
 * the test client would log with the password.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class PasswordLengthIntegrationTest {

    private static final String MESSAGE = "Password must be at most 72 bytes in UTF-8 (an accented letter takes 2, an emoji 4)";
    private static final String MIN_LENGTH_MESSAGE = "New password must be at least 8 characters long";
    private static final String PASSWORD = "Password123!";
    /** 36 accented letters: 72 bytes, the longest password BCrypt hashes. */
    private static final String LONGEST_PASSWORD = "é".repeat(36);
    /** 37 accented letters: 74 bytes, although far under 72 characters. */
    private static final String MULTIBYTE_TOO_LONG = "é".repeat(37);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void register_WithAPasswordOver72Bytes_ShouldAnswerAValidationErrorAndCreateNoAccount(CapturedOutput output) {
        String email = newEmail();
        for (String tooLong : new String[] {"a".repeat(80), MULTIBYTE_TOO_LONG}) {
            ResponseEntity<Map> rejected = post("/auth/register", register(email, tooLong), null);
            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(rejected.getBody().get("validationErrors")).isEqualTo(Map.of("password", MESSAGE));
        }

        // The email is still free, and a password of exactly 72 bytes registers and signs in.
        assertThat(post("/auth/register", register(email, LONGEST_PASSWORD), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/auth/login", login(email, LONGEST_PASSWORD), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Rejected by validation, logged at WARN with the field and constraint, before BCrypt could refuse it.
        assertThat(output.getAll().lines()).anyMatch(line -> line.contains(" WARN ") &&
            line.endsWith("Validation failed for registerRequest: password (PasswordMaxBytes)"));
        assertThat(output.getAll()).doesNotContain("Registration rejected").doesNotContain("password cannot be more than 72 bytes");
    }

    @Test
    void changePassword_ToAPasswordOver72Bytes_ShouldAnswerAValidationErrorAndKeepTheCurrentOne(CapturedOutput output) {
        String email = newEmail();
        ResponseEntity<Map> account = post("/auth/register", register(email, PASSWORD), null);
        String token = (String) account.getBody().get("token");

        ResponseEntity<Map> rejected = post("/users/change-password", passwordChange(MULTIBYTE_TOO_LONG), token);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().get("validationErrors")).isEqualTo(Map.of("newPassword", MESSAGE));
        assertThat(post("/auth/login", login(email, PASSWORD), null).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(post("/users/change-password", passwordChange(LONGEST_PASSWORD), token, Boolean.class).getBody()).isTrue();
        assertThat(post("/auth/login", login(email, LONGEST_PASSWORD), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(output.getAll().lines()).anyMatch(line -> line.contains(" WARN ") &&
            line.endsWith("Validation failed for passwordChangeRequestDto: newPassword (PasswordMaxBytes)"));
        assertThat(output.getAll()).doesNotContain("password cannot be more than 72 bytes");
    }

    @Test
    void changePassword_ToAPasswordUnder8Characters_ShouldAnswerAValidationErrorAndKeepTheCurrentOne(CapturedOutput output) {
        String email = newEmail();
        String token = (String) post("/auth/register", register(email, PASSWORD), null).getBody().get("token");

        for (String tooShort : new String[] {"a", "Abcdef1"}) {
            ResponseEntity<Map> rejected = post("/users/change-password", passwordChange(tooShort), token);
            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(rejected.getBody().get("validationErrors")).isEqualTo(Map.of("newPassword", MIN_LENGTH_MESSAGE));
        }
        assertThat(post("/auth/login", login(email, "a"), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/auth/login", login(email, PASSWORD), null).getStatusCode()).isEqualTo(HttpStatus.OK);

        // 8 characters, the minimum the profile form asks for, is enough.
        assertThat(post("/users/change-password", passwordChange("Abcdef12"), token, Boolean.class).getBody()).isTrue();
        assertThat(post("/auth/login", login(email, "Abcdef12"), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(output.getAll().lines()).anyMatch(line -> line.contains(" WARN ") &&
            line.endsWith("Validation failed for passwordChangeRequestDto: newPassword (Size)"));
    }

    @Test
    void changePassword_WithAConfirmationThatDiffers_ShouldAnswerBadRequestAndKeepTheCurrentOne() {
        String email = newEmail();
        String token = (String) post("/auth/register", register(email, PASSWORD), null).getBody().get("token");
        PasswordChangeRequestDto mismatched = passwordChange("NewPassword456!");
        mismatched.setConfirmPassword("NewPassword457!");

        ResponseEntity<Map> rejected = post("/users/change-password", mismatched, token);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().get("message")).isEqualTo("New password and confirmation do not match");
        assertThat(post("/auth/login", login(email, "NewPassword456!"), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/auth/login", login(email, PASSWORD), null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> post(String path, Object body, String token) {
        return post(path, body, token, Map.class);
    }

    private <T> ResponseEntity<T> post(String path, Object body, String token, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange("http://localhost:" + port + "/api" + path, HttpMethod.POST, new HttpEntity<>(body, headers), type);
    }

    private static String newEmail() {
        return "password.length." + UUID.randomUUID() + "@mail.example";
    }

    private static RegisterRequest register(String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private static LoginRequest login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private static PasswordChangeRequestDto passwordChange(String newPassword) {
        PasswordChangeRequestDto request = new PasswordChangeRequestDto();
        request.setCurrentPassword(PASSWORD);
        request.setNewPassword(newPassword);
        request.setConfirmPassword(newPassword);
        return request;
    }
}
