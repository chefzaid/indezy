package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.dto.LoginResponse;
import dev.swirlit.indezy.dto.RegisterRequest;
import dev.swirlit.indezy.service.AuthService;
import dev.swirlit.indezy.service.LoginAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
@Tag(name = "Authentication", description = "Authentication and user registration operations")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptService loginAttemptService;

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and return JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "429", description = "Too many failed attempts"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
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
        } catch (Exception e) {
            loginAttemptService.loginFailed(request.getEmail());
            log.error("Login failed for email: {}", request.getEmail(), e);
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register new user and return JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration successful"),
            @ApiResponse(responseCode = "409", description = "User already exists"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
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
}
