package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.dto.LoginResponse;
import dev.swirlit.indezy.dto.RegisterRequest;
import dev.swirlit.indezy.exception.AccountAlreadyExistsException;
import dev.swirlit.indezy.exception.ExceptionLogDetails;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.exception.TwoFactorRequiredException;
import dev.swirlit.indezy.service.AuthService;
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

    @GetMapping("/sso")
    @Operation(summary = "Keycloak SSO", description = "Exchange the ingress-validated Keycloak identity for an Indezy session")
    public ResponseEntity<LoginResponse> sso(
            @RequestHeader(value = "X-Auth-Request-Access-Token", required = false) String accessToken) {
        try {
            return ResponseEntity.ok(authService.loginWithKeycloak(accessToken));
        } catch (AuthenticationException | BadJwtException exception) {
            // Fixed text or a token validation error (expired, wrong issuer or audience), never a claim value.
            log.warn("Keycloak SSO exchange rejected: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (RuntimeException exception) {
            logFailure("Keycloak SSO exchange", exception);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
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
