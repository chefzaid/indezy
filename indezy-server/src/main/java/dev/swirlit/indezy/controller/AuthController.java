package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.dto.LoginResponse;
import dev.swirlit.indezy.dto.RegisterRequest;
import dev.swirlit.indezy.exception.AccountOnHoldException;
import dev.swirlit.indezy.exception.EmailUnverifiedException;
import dev.swirlit.indezy.exception.AccountAlreadyExistsException;
import dev.swirlit.indezy.exception.ExceptionLogDetails;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.exception.TwoFactorRequiredException;
import dev.swirlit.indezy.service.AccountEmailService;
import dev.swirlit.indezy.service.AuthService;
import dev.swirlit.indezy.validation.PasswordMaxBytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import dev.swirlit.indezy.service.LoginAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.web.bind.annotation.*;

/**
 * Login, registration and Keycloak SSO. The email and any other request value are personal data and are never logged:
 * rejected requests (wrong credentials, a locked account, an existing account) are client errors logged at WARN without
 * a stack trace, and only unexpected faults are logged at ERROR with their stack trace.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication and user registration operations")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptService loginAttemptService;
    private final AccountEmailService accountEmails;

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and return JWT token")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.debug("POST /auth/login - Login attempt");
        if (loginAttemptService.isBlocked(request.getEmail())) {
            log.warn("Login blocked after too many failed attempts");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        try {
            LoginResponse response = authService.login(request);
            loginAttemptService.loginSucceeded(request.getEmail());
            return ResponseEntity.ok(response);
        } catch (AccountOnHoldException e) {
            // The credentials were right: the account waits for its deletion, and signing in with restore takes it back.
            loginAttemptService.loginSucceeded(request.getEmail());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(onHold(e));
        } catch (EmailUnverifiedException e) {
            // The credentials were right: the account first confirms its email, through the link sent (or sent again).
            loginAttemptService.loginSucceeded(request.getEmail());
            LoginResponse unverified = new LoginResponse();
            unverified.setEmailVerificationRequired(true);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(unverified);
        } catch (TwoFactorRequiredException e) {
            // Not a failed attempt: the password was right, the client must now ask for the code.
            LoginResponse challenge = new LoginResponse();
            challenge.setTwoFactorRequired(true);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(challenge);
        } catch (ResourceNotFoundException e) {
            // AuthService reports an unknown email, a wrong password and a wrong two-factor code alike.
            loginAttemptService.loginFailed(request.getEmail());
            log.warn("Login rejected: invalid credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (RuntimeException e) {
            loginAttemptService.loginFailed(request.getEmail());
            logFailure("Login", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register new user and return JWT token")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.debug("POST /auth/register - Registration attempt");
        try {
            LoginResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (AccountAlreadyExistsException e) {
            log.warn("Registration rejected: an account already exists for this email");
            return ResponseEntity.status(409).build();
        } catch (IllegalArgumentException e) {
            // Invalid input that validation missed (it rejects a password over BCrypt's 72 bytes) is not an existing
            // account, so 400; its message may quote the input, so only its origin is logged.
            log.warn("Registration rejected at {}", ExceptionLogDetails.originOf(e));
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            logFailure("Registration", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /** A confirmation or reset link's secret. */
    public record LinkRequest(@NotBlank @Size(max = 100) String token) {

        @Override
        public String toString() {
            return "LinkRequest[]";
        }
    }

    /** An address to send a link to. */
    public record EmailRequest(@NotBlank @Size(max = 254) String email) {

        @Override
        public String toString() {
            return "EmailRequest[]";
        }
    }

    /** The reset link's secret and the new password. */
    public record ResetRequest(@NotBlank @Size(max = 100) String token,
                               @NotBlank @Size(min = 8, message = "Password must be at least 8 characters long") @PasswordMaxBytes String password) {

        @Override
        public String toString() {
            return "ResetRequest[]";
        }
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirm the email of an account registered with a password, and sign in")
    public ResponseEntity<LoginResponse> verifyEmail(@Valid @RequestBody LinkRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request.token()));
    }

    @PostMapping("/verify-email/resend")
    @Operation(summary = "Send the email confirmation link again", description = "Answers the same whether the address has an account or not")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody EmailRequest request) {
        accountEmails.resendVerification(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Send a link to set a new password", description = "Answers the same whether the address has an account or not")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody EmailRequest request) {
        accountEmails.requestReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Set a new password from the reset link, and sign in", description = "Every other session of the account ends")
    public ResponseEntity<LoginResponse> resetPassword(@Valid @RequestBody ResetRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request.token(), request.password()));
    }

    @GetMapping("/sso")
    @Operation(summary = "Keycloak SSO", description = "Exchange the ingress-validated Keycloak identity for an Indezy session")
    public ResponseEntity<LoginResponse> sso(
            @RequestHeader(value = "X-Auth-Request-Access-Token", required = false) String accessToken,
            @RequestParam(defaultValue = "false") boolean restore) {
        try {
            return ResponseEntity.ok(authService.loginWithKeycloak(accessToken, restore));
        } catch (AccountOnHoldException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(onHold(e));
        } catch (AuthenticationException | BadJwtException exception) {
            // Fixed text or a token validation error (expired, wrong issuer or audience), never a claim value.
            log.warn("Keycloak SSO exchange rejected: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (RuntimeException exception) {
            logFailure("Keycloak SSO exchange", exception);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private static LoginResponse onHold(AccountOnHoldException e) {
        LoginResponse response = new LoginResponse();
        response.setAccountOnHold(true);
        response.setPurgeOn(e.getPurgeOn());
        return response;
    }

    /**
     * A database constraint error (two requests creating the same account at once, a name too long for its column) is
     * logged at WARN with its constraint and SQL state but without its message, which quotes the stored values, the
     * email included. Any other fault is unexpected and keeps its stack trace at ERROR.
     */
    private static void logFailure(String operation, RuntimeException exception) {
        if (exception instanceof DataIntegrityViolationException violation) {
            log.warn("{} rejected by a database constraint: {}", operation, ExceptionLogDetails.describe(violation));
        } else {
            log.error("{} failed with an unexpected error", operation, exception);
        }
    }
}
