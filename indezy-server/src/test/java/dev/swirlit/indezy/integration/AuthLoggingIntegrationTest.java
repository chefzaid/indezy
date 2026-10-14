package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.LoginRequest;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replays the reported auth flow over HTTP (registration, duplicate registration, wrong passwords up to the lockout) and
 * reads the whole log: neither the email nor a password ever appears, at any level, and the rejections are logged at
 * WARN, not ERROR. The test profile logs Spring MVC and Spring Security at DEBUG, where Spring MVC and the test client
 * print each request and response body through its {@code toString}, so the auth DTOs are posted as they are.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuthLoggingIntegrationTest {

    /** {@code LoginAttemptService} locks an account after five failed logins. */
    private static final int ATTEMPTS_BEFORE_LOCKOUT = 5;
    private static final String PASSWORD = "Password123!";
    private static final String WRONG_PASSWORD = "WrongPassword1!";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void rejectedRegistrationAndLogins_ShouldLogAtWarnWithoutTheEmailOrPasswords(CapturedOutput output) {
        String accountKey = UUID.randomUUID().toString();
        String email = "jane.private." + accountKey + "@mail.example";
        RegisterRequest account = new RegisterRequest();
        account.setFirstName("Jane");
        account.setLastName("Private-" + accountKey);
        account.setEmail(email);
        account.setPassword(PASSWORD);

        assertThat(post("/auth/register", account).value()).isEqualTo(200);
        assertThat(post("/auth/register", account).value()).isEqualTo(409);
        for (int attempt = 0; attempt < ATTEMPTS_BEFORE_LOCKOUT; attempt++) {
            assertThat(post("/auth/login", login(email, WRONG_PASSWORD)).value()).isEqualTo(401);
        }
        assertThat(post("/auth/login", login(email, PASSWORD)).value()).isEqualTo(429);

        // The application logs at DEBUG in every profile, so the debug lines of the flow are read as well.
        assertThat(authFlowLines(output)).anyMatch(line -> line.contains("DEBUG") && line.contains("Registration successful for user ID"));
        assertThat(authFlowLine(output, "Registration rejected: an account already exists for this email")).contains("WARN");
        assertThat(authFlowLine(output, "Login rejected: invalid credentials")).contains("WARN");
        assertThat(authFlowLine(output, "Login blocked after too many failed attempts")).contains("WARN");
        assertThat(authFlowLines(output)).noneMatch(line -> line.contains("ERROR"));
        // Spring MVC (Read) and the test client (Writing) print the bodies, without any of their fields.
        assertThat(output.getAll()).contains("to [RegisterRequest()]", "to [LoginRequest()]", "Writing [LoginRequest()]");
        assertThat(output).doesNotContain(email).doesNotContain(account.getLastName()).doesNotContain(PASSWORD).doesNotContain(WRONG_PASSWORD);
    }

    private HttpStatusCode post(String path, Object body) {
        return restTemplate.postForEntity("http://localhost:" + port + "/api" + path, body, String.class).getStatusCode();
    }

    private static LoginRequest login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
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
