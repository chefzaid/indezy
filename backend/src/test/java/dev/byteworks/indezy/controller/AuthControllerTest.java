package dev.byteworks.indezy.controller;

import dev.byteworks.indezy.dto.LoginRequest;
import dev.byteworks.indezy.dto.LoginResponse;
import dev.byteworks.indezy.dto.RegisterRequest;
import dev.byteworks.indezy.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void login_ShouldReturnLoginResponse_WhenCredentialsAreValid() {
        // Given
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(1L);
        userInfo.setEmail("test@example.com");
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
        assertThat(response.getBody().getUser().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getBody().getUser().getFirstName()).isEqualTo("John");
        assertThat(response.getBody().getUser().getLastName()).isEqualTo("Doe");
    }

    @Test
    void login_ShouldReturnUnauthorized_WhenCredentialsAreInvalid() {
        // Given
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("wrongpassword");

        when(authService.login(any(LoginRequest.class))).thenThrow(new RuntimeException("Invalid credentials"));

        // When
        ResponseEntity<LoginResponse> response = authController.login(loginRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void register_ShouldReturnLoginResponse_WhenDataIsValid() {
        // Given
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(1L);
        userInfo.setEmail("test@example.com");
        userInfo.setFirstName("John");
        userInfo.setLastName("Doe");

        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setToken("jwt-token");
        loginResponse.setUser(userInfo);

        when(authService.register(any(RegisterRequest.class))).thenReturn(loginResponse);

        // When
        ResponseEntity<LoginResponse> response = authController.register(registerRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isEqualTo("jwt-token");
    }

    @Test
    void register_ShouldReturnConflict_WhenUserAlreadyExists() {
        // Given
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");
        registerRequest.setEmail("existing@example.com");
        registerRequest.setPassword("password123");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("User with this email already exists"));

        // When
        ResponseEntity<LoginResponse> response = authController.register(registerRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
