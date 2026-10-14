package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.dto.LoginResponse;
import dev.swirlit.indezy.exception.AccountAlreadyExistsException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.exception.TwoFactorRequiredException;
import dev.swirlit.indezy.dto.RegisterRequest;
import dev.swirlit.indezy.service.AuthService;
import dev.swirlit.indezy.service.LoginAttemptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Besides the responses, checks the captured log: the email never appears, rejected requests are logged at WARN without
 * a stack trace, and only unexpected faults reach ERROR with theirs.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AuthControllerTest {

    private static final String EMAIL = "jane.private@mail.example";
    private static final String DUPLICATE_KEY_MESSAGE =
            "could not execute statement [Unique index or primary key violation: \"PUBLIC.USERS(EMAIL) VALUES ('" + EMAIL + "')\"]";

    @Mock
    private AuthService authService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthController authController;

    @Test
    void login_ShouldReturnLoginResponse_WhenCredentialsAreValid() {
        // Given
        LoginRequest loginRequest = loginRequest("password123");

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(1L);
        userInfo.setEmail(EMAIL);
        userInfo.setFirstName("John");
        userInfo.setLastName("Doe");

        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setToken("jwt-token");
        loginResponse.setUser(userInfo);

        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        // When
        ResponseEntity<LoginResponse> response = authController.login(loginRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isEqualTo("jwt-token");
        assertThat(response.getBody().getUser().getId()).isEqualTo(1L);
        assertThat(response.getBody().getUser().getEmail()).isEqualTo(EMAIL);
        assertThat(response.getBody().getUser().getFirstName()).isEqualTo("John");
        assertThat(response.getBody().getUser().getLastName()).isEqualTo("Doe");
        verify(loginAttemptService).loginSucceeded(EMAIL);
    }

    @Test
    void login_WithWrongPassword_ShouldRecordTheFailureAndLogItAtWarnWithoutTheEmail(CapturedOutput output) {
        // Given
        when(authService.login(any(LoginRequest.class))).thenThrow(new ResourceNotFoundException("Invalid email or password"));

        // When
        ResponseEntity<LoginResponse> response = authController.login(loginRequest("wrongpassword"));

        // Then the failure is recorded for brute-force tracking and logged as a client error.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNull();
        verify(loginAttemptService).loginFailed(EMAIL);
        assertLoggedAtWarnOnly(output, "Login rejected: invalid credentials");
        assertThat(output).doesNotContain(EMAIL).doesNotContain(ResourceNotFoundException.class.getName());
    }

    @Test
    void login_WhenTwoFactorCodeIsMissing_ShouldChallengeWithoutCountingAFailure(CapturedOutput output) {
        when(authService.login(any(LoginRequest.class))).thenThrow(new TwoFactorRequiredException());

        ResponseEntity<LoginResponse> response = authController.login(loginRequest("password123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getTwoFactorRequired()).isTrue();
        verify(loginAttemptService, never()).loginFailed(any());
        assertThat(output).doesNotContain(EMAIL);
        assertThat(controllerLines(output)).noneMatch(line -> line.contains("WARN") || line.contains("ERROR"));
    }

    @Test
    void login_ShouldReturnTooManyRequests_WhenAccountIsBlocked(CapturedOutput output) {
        // Given the account is already locked out.
        when(loginAttemptService.isBlocked(EMAIL)).thenReturn(true);

        // When
        ResponseEntity<LoginResponse> response = authController.login(loginRequest("password123"));

        // Then authentication is never attempted, a 429 is returned and the lockout is logged without the email.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(authService, never()).login(any(LoginRequest.class));
        assertLoggedAtWarnOnly(output, "Login blocked after too many failed attempts");
        assertThat(output).doesNotContain(EMAIL);
    }

    @Test
    void login_WhenAnUnexpectedErrorOccurs_ShouldStillCountTheFailureAndLogItAtErrorWithoutTheEmail(CapturedOutput output) {
        when(authService.login(any(LoginRequest.class))).thenThrow(new IllegalStateException("Database unavailable"));

        ResponseEntity<LoginResponse> response = authController.login(loginRequest("password123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(loginAttemptService).loginFailed(EMAIL);
        assertThat(logLine(output, "Login failed with an unexpected error")).contains("ERROR");
        assertThat(output).contains("java.lang.IllegalStateException: Database unavailable", "at " + AuthControllerTest.class.getName());
        assertThat(output).doesNotContain(EMAIL);
    }

    @Test
    void login_WhenAConcurrentRequestCreatesTheProfile_ShouldLogTheConstraintAtWarnWithoutTheDatabaseMessage(CapturedOutput output) {
        when(authService.login(any(LoginRequest.class))).thenThrow(new DataIntegrityViolationException(DUPLICATE_KEY_MESSAGE));

        ResponseEntity<LoginResponse> response = authController.login(loginRequest("password123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(loginAttemptService).loginFailed(EMAIL);
        assertLoggedAtWarnOnly(output, "Login rejected by a database constraint: constraint unknown, SQL state unknown");
        assertThat(output).doesNotContain(EMAIL).doesNotContain("Unique index");
    }

    @Test
    void register_ShouldReturnLoginResponse_WhenDataIsValid() {
        // Given
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(1L);
        userInfo.setEmail(EMAIL);
        userInfo.setFirstName("John");
        userInfo.setLastName("Doe");

        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setToken("jwt-token");
        loginResponse.setUser(userInfo);

        when(authService.register(any(RegisterRequest.class))).thenReturn(loginResponse);

        // When
        ResponseEntity<LoginResponse> response = authController.register(registerRequest());

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isEqualTo("jwt-token");
    }

    @Test
    void register_ShouldReturnConflictAndLogAtWarnWithoutTheEmail_WhenUserAlreadyExists(CapturedOutput output) {
        // Given
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new AccountAlreadyExistsException("User with this email already exists: " + EMAIL));

        // When
        ResponseEntity<LoginResponse> response = authController.register(registerRequest());

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNull();
        assertLoggedAtWarnOnly(output, "Registration rejected: an account already exists for this email");
        assertThat(output).doesNotContain(EMAIL).doesNotContain(AccountAlreadyExistsException.class.getName());
    }

    @Test
    void register_WhenThePasswordCannotBeHashed_ShouldReturnBadRequestAndLogOnlyWhereItWasRejected(CapturedOutput output) {
        // An IllegalArgumentException that is not a duplicate account, such as BCrypt's refusal of a password over 72
        // bytes should validation ever let one through, is invalid input, not a conflict.
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("password cannot be more than 72 bytes"));

        ResponseEntity<LoginResponse> response = authController.register(registerRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Mockito fills in the stack trace when the mocked call throws, so the origin is the service method.
        assertLoggedAtWarnOnly(output, "Registration rejected at " + AuthService.class.getName() + ".register:");
        assertThat(output).doesNotContain(EMAIL).doesNotContain("72 bytes").doesNotContain("already exists");
    }

    @Test
    void register_WhenAConcurrentRegistrationWins_ShouldReturnBadRequestAndLogTheConstraintAtWarnWithoutTheEmail(CapturedOutput output) {
        SQLException sqlException = new SQLException(DUPLICATE_KEY_MESSAGE, "23505");
        when(authService.register(any(RegisterRequest.class))).thenThrow(new DataIntegrityViolationException("could not execute statement",
                new org.hibernate.exception.ConstraintViolationException(DUPLICATE_KEY_MESSAGE, sqlException, "uk_users_email")));

        ResponseEntity<LoginResponse> response = authController.register(registerRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertLoggedAtWarnOnly(output, "Registration rejected by a database constraint: constraint uk_users_email, SQL state 23505");
        assertThat(output).doesNotContain(EMAIL).doesNotContain("Unique index");
    }

    @Test
    void register_WhenAnUnexpectedErrorOccurs_ShouldReturnBadRequestAndLogItAtErrorWithoutTheEmail(CapturedOutput output) {
        when(authService.register(any(RegisterRequest.class))).thenThrow(new IllegalStateException("Token signing failed"));

        ResponseEntity<LoginResponse> response = authController.register(registerRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(logLine(output, "Registration failed with an unexpected error")).contains("ERROR");
        assertThat(output).contains("java.lang.IllegalStateException: Token signing failed").doesNotContain(EMAIL);
    }

    @Test
    void sso_ShouldReturnUnauthorizedAndLogTheReasonAtWarn_WhenIngressIdentityIsMissing(CapturedOutput output) {
        when(authService.loginWithKeycloak(null)).thenThrow(new BadCredentialsException("Keycloak access token is missing"));

        ResponseEntity<LoginResponse> response = authController.sso(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertLoggedAtWarnOnly(output, "Keycloak SSO exchange rejected: Keycloak access token is missing");
        assertThat(output).doesNotContain(BadCredentialsException.class.getName());
    }

    @Test
    void sso_ShouldReturnUnauthorizedAndLogTheReasonAtWarn_WhenTheTokenIsInvalid(CapturedOutput output) {
        when(authService.loginWithKeycloak("expired-token")).thenThrow(new BadJwtException("Jwt expired at 2026-10-14T08:00:00Z"));

        ResponseEntity<LoginResponse> response = authController.sso("expired-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertLoggedAtWarnOnly(output, "Keycloak SSO exchange rejected: Jwt expired at 2026-10-14T08:00:00Z");
        assertThat(output).doesNotContain(BadJwtException.class.getName());
    }

    @Test
    void sso_WhenTheKeySetCannotBeFetched_ShouldReturnUnauthorizedAndLogItAtError(CapturedOutput output) {
        when(authService.loginWithKeycloak("keycloak-token")).thenThrow(new JwtException("Couldn't retrieve remote JWK set"));

        ResponseEntity<LoginResponse> response = authController.sso("keycloak-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(logLine(output, "Keycloak SSO exchange failed with an unexpected error")).contains("ERROR");
        assertThat(output).contains(JwtException.class.getName() + ": Couldn't retrieve remote JWK set");
    }

    @Test
    void sso_WhenAConcurrentSignInCreatesTheAccount_ShouldLogTheConstraintAtWarnWithoutTheEmail(CapturedOutput output) {
        when(authService.loginWithKeycloak("keycloak-token")).thenThrow(new DataIntegrityViolationException(DUPLICATE_KEY_MESSAGE));

        ResponseEntity<LoginResponse> response = authController.sso("keycloak-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertLoggedAtWarnOnly(output, "Keycloak SSO exchange rejected by a database constraint");
        assertThat(output).doesNotContain(EMAIL);
    }

    @Test
    void sso_ShouldReturnSession_WhenIngressIdentityIsValid() {
        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setToken("jwt-token");
        when(authService.loginWithKeycloak("keycloak-token")).thenReturn(loginResponse);

        ResponseEntity<LoginResponse> response = authController.sso("keycloak-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(loginResponse);
    }

    private static LoginRequest loginRequest(String password) {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(EMAIL);
        loginRequest.setPassword(password);
        return loginRequest;
    }

    private static RegisterRequest registerRequest() {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");
        registerRequest.setEmail(EMAIL);
        registerRequest.setPassword("password123");
        return registerRequest;
    }

    /** The marker line is logged at WARN and the controller logs nothing at ERROR. */
    private static void assertLoggedAtWarnOnly(CapturedOutput output, String marker) {
        assertThat(logLine(output, marker)).contains("WARN");
        assertThat(controllerLines(output)).noneMatch(line -> line.contains("ERROR"));
    }

    private static String logLine(CapturedOutput output, String marker) {
        return controllerLines(output).stream()
                .filter(line -> line.contains(marker))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log line contains: " + marker + "\n" + output.getAll()));
    }

    /** Only the controller's own lines: the capture also holds JVM and Mockito warnings. */
    private static List<String> controllerLines(CapturedOutput output) {
        return output.getAll().lines().filter(line -> line.contains(AuthController.class.getSimpleName())).toList();
    }
}
