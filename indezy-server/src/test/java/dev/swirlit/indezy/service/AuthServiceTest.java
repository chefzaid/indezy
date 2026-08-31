package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.dto.LoginResponse;
import dev.swirlit.indezy.dto.RegisterRequest;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FreelanceRepository freelanceRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private JwtDecoder keycloakJwtDecoder;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private Freelance testFreelance;
    private LoginRequest loginRequest;
    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setFirstName("John");
        testUser.setLastName("Doe");
        testUser.setPasswordHash("hashedPassword");

        testFreelance = new Freelance();
        testFreelance.setId(41L);
        testFreelance.setEmail("test@example.com");
        lenient().when(freelanceRepository.findByEmail(any(String.class)))
                .thenReturn(Optional.of(testFreelance));

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");

        registerRequest = new RegisterRequest();
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");
    }

    @Test
    void login_ShouldReturnLoginResponse_WhenCredentialsAreValid() {
        // Given
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(jwtUtil.generateToken("test@example.com", 1L)).thenReturn("jwt-token");

        // When
        LoginResponse response = authService.login(loginRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getId()).isEqualTo(41L);
        assertThat(response.getUser().getAccountId()).isEqualTo(1L);
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getUser().getFirstName()).isEqualTo("John");
        assertThat(response.getUser().getLastName()).isEqualTo("Doe");
    }

    @Test
    void login_ShouldThrowException_WhenUserNotFound() {
        // Given
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_ShouldThrowException_WhenAccountIsSoftDeleted() {
        // Given a soft-deleted account
        testUser.setDeletedAt(java.time.LocalDateTime.now());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        // When & Then login is refused with the generic error and the password is never checked.
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Invalid email or password");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_ShouldThrowException_WhenPasswordIsInvalid() {
        // Given
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void register_ShouldReturnLoginResponse_WhenDataIsValid() {
        // Given
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtUtil.generateToken("test@example.com", 1L)).thenReturn("jwt-token");

        // When
        LoginResponse response = authService.register(registerRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getId()).isEqualTo(41L);
        assertThat(response.getUser().getAccountId()).isEqualTo(1L);
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
        assertThat(response.getUser().getFirstName()).isEqualTo("John");
        assertThat(response.getUser().getLastName()).isEqualTo("Doe");

        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_ShouldThrowException_WhenUserAlreadyExists() {
        // Given
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        // When & Then
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User with this email already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginWithKeycloak_ShouldLinkVerifiedIdentityAndReturnLocalSession() {
        Jwt identity = Jwt.withTokenValue("keycloak-token")
                .header("alg", "RS256")
                .subject("keycloak-user-id")
                .claim("email", "test@example.com")
                .claim("email_verified", true)
                .claim("preferred_username", "testuser")
                .build();
        when(keycloakJwtDecoder.decode("keycloak-token")).thenReturn(identity);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateToken("test@example.com", 1L)).thenReturn("jwt-token");

        LoginResponse response = authService.loginWithKeycloak("keycloak-token");

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getEmail()).isEqualTo("test@example.com");
        verify(keycloakJwtDecoder).decode("keycloak-token");
    }

    @Test
    void loginWithKeycloak_ShouldProvisionAProfileForANewVerifiedIdentity() {
        Jwt identity = Jwt.withTokenValue("keycloak-token")
                .header("alg", "RS256")
                .subject("keycloak-user-id")
                .claim("email", "ZAID@swirlit.dev")
                .claim("email_verified", true)
                .claim("preferred_username", "zaid")
                .build();
        when(keycloakJwtDecoder.decode("keycloak-token")).thenReturn(identity);
        when(userRepository.findByEmail("zaid@swirlit.dev")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any(String.class))).thenReturn("unusable-random-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        when(freelanceRepository.findByEmail("zaid@swirlit.dev")).thenReturn(Optional.empty());
        when(freelanceRepository.save(any(Freelance.class))).thenAnswer(invocation -> {
            Freelance saved = invocation.getArgument(0);
            saved.setId(8L);
            return saved;
        });
        when(jwtUtil.generateToken("zaid@swirlit.dev", 7L)).thenReturn("jwt-token");

        LoginResponse response = authService.loginWithKeycloak("keycloak-token");

        assertThat(response.getUser().getFirstName()).isEqualTo("zaid");
        assertThat(response.getUser().getLastName()).isEqualTo("User");
        assertThat(response.getUser().getEmail()).isEqualTo("zaid@swirlit.dev");
        assertThat(response.getUser().getId()).isEqualTo(8L);
        assertThat(response.getUser().getAccountId()).isEqualTo(7L);
        verify(userRepository).save(argThat(user ->
                "unusable-random-hash".equals(user.getPasswordHash())));
        verify(freelanceRepository).save(argThat(freelance ->
                freelance.getStatus() == EmploymentStatus.FREELANCE
                        && "zaid@swirlit.dev".equals(freelance.getEmail())));
    }

    @Test
    void loginWithKeycloak_ShouldRejectMissingOrIncompleteIdentity() {
        assertThatThrownBy(() -> authService.loginWithKeycloak(" "))
                .isInstanceOf(BadCredentialsException.class);

        Jwt identity = Jwt.withTokenValue("keycloak-token")
                .header("alg", "RS256")
                .subject("keycloak-user-id")
                .claim("email", "test@example.com")
                .claim("email_verified", false)
                .claim("preferred_username", "testuser")
                .build();
        when(keycloakJwtDecoder.decode("keycloak-token")).thenReturn(identity);

        assertThatThrownBy(() -> authService.loginWithKeycloak("keycloak-token"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginWithKeycloak_ShouldRejectSoftDeletedAccount() {
        Jwt identity = Jwt.withTokenValue("keycloak-token")
                .header("alg", "RS256")
                .subject("keycloak-user-id")
                .claim("email", "test@example.com")
                .claim("email_verified", true)
                .claim("preferred_username", "testuser")
                .build();
        testUser.setDeletedAt(java.time.LocalDateTime.now());
        when(keycloakJwtDecoder.decode("keycloak-token")).thenReturn(identity);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.loginWithKeycloak("keycloak-token"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
