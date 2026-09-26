package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.dto.LoginResponse;
import dev.swirlit.indezy.dto.RegisterRequest;
import dev.swirlit.indezy.exception.TwoFactorRequiredException;
import dev.swirlit.indezy.service.AuthService;
import dev.swirlit.indezy.service.LoginAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        log.debug("POST /auth/login - Login attempt for email: {}", request.getEmail());
        if (loginAttemptService.isBlocked(request.getEmail())) {
            log.warn("Login blocked after too many failed attempts for email: {}", request.getEmail());
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
        } catch (Exception e) {
            loginAttemptService.loginFailed(request.getEmail());
            log.error("Login failed for email: {}", request.getEmail(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register new user and return JWT token")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.debug("POST /auth/register - Registration attempt for email: {}", request.getEmail());
        try {
            LoginResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Registration failed for email: {}", request.getEmail(), e);
            return ResponseEntity.status(409).build();
        } catch (Exception e) {
            log.error("Registration failed for email: {}", request.getEmail(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/sso")
    @Operation(summary = "Keycloak SSO", description = "Exchange the ingress-validated Keycloak identity for an Indezy session")
    public ResponseEntity<LoginResponse> sso(
            @RequestHeader(value = "X-Auth-Request-Access-Token", required = false) String accessToken) {
        try {
            return ResponseEntity.ok(authService.loginWithKeycloak(accessToken));
        } catch (RuntimeException exception) {
            log.warn("Keycloak SSO exchange rejected: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
