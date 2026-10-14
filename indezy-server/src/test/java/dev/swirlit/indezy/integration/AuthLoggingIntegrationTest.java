package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replays the reported auth flow over HTTP (registration, duplicate registration, wrong passwords up to the lockout) and
 * reads the whole log: the email never appears, at any level, and the rejections are logged at WARN, not ERROR. Spring
 * MVC and Spring Security keep their production level (WARN): the test profile raises them to DEBUG, where Spring MVC and
 * the test client print the request bodies.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"logging.level.org.springframework.web=WARN", "logging.level.org.springframework.security=WARN"})
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuthLoggingIntegrationTest {

    /** {@code LoginAttemptService} locks an account after five failed logins. */
    private static final int ATTEMPTS_BEFORE_LOCKOUT = 5;
    private static final String PASSWORD = "Password123!";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void rejectedRegistrationAndLogins_ShouldLogAtWarnWithoutTheEmail(CapturedOutput output) {
        String email = "jane.private." + UUID.randomUUID() + "@mail.example";
        Map<String, String> account = Map.of("firstName", "Jane", "lastName", "Private", "email", email, "password", PASSWORD);

        assertThat(post("/auth/register", account).value()).isEqualTo(200);
        assertThat(post("/auth/register", account).value()).isEqualTo(409);
        for (int attempt = 0; attempt < ATTEMPTS_BEFORE_LOCKOUT; attempt++) {
            assertThat(post("/auth/login", Map.of("email", email, "password", "WrongPassword1!")).value()).isEqualTo(401);
        }
        assertThat(post("/auth/login", Map.of("email", email, "password", PASSWORD)).value()).isEqualTo(429);

        // The application logs at DEBUG in every profile, so the debug lines of the flow are read as well.
        assertThat(authFlowLines(output)).anyMatch(line -> line.contains("DEBUG") && line.contains("Registration successful for user ID"));
        assertThat(authFlowLine(output, "Registration rejected: an account already exists for this email")).contains("WARN");
        assertThat(authFlowLine(output, "Login rejected: invalid credentials")).contains("WARN");
        assertThat(authFlowLine(output, "Login blocked after too many failed attempts")).contains("WARN");
        assertThat(authFlowLines(output)).noneMatch(line -> line.contains("ERROR"));
        assertThat(output).doesNotContain(email);
    }

    private HttpStatusCode post(String path, Map<String, String> body) {
        return restTemplate.postForEntity("http://localhost:" + port + "/api" + path, body, String.class).getStatusCode();
    }

    private static String authFlowLine(CapturedOutput output, String marker) {
        return authFlowLines(output).stream()
            .filter(line -> line.contains(marker))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No log line contains: " + marker + "\n" + output.getAll()));
    }

    /** Lines logged by the login and registration flow, as opposed to the framework's own request logging. */
    private static List<String> authFlowLines(CapturedOutput output) {
        return output.getAll().lines().filter(line -> line.contains("AuthController") || line.contains("AuthService")).toList();
    }
}
