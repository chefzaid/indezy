package dev.byteworks.indezy.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-for-jwt-token-generation-and-validation");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L); // 24 hours
    }

    @Test
    void generateToken_ShouldCreateValidToken() {
        // Given
        String username = "test@example.com";
        Long userId = 1L;

        // When
        String token = jwtUtil.generateToken(username, userId);

        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts separated by dots
    }

    @Test
    void extractUsername_ShouldReturnCorrectUsername() {
        // Given
        String username = "test@example.com";
        Long userId = 1L;
        String token = jwtUtil.generateToken(username, userId);

        // When
        String extractedUsername = jwtUtil.extractUsername(token);

        // Then
        assertThat(extractedUsername).isEqualTo(username);
    }

    @Test
    void extractUserId_ShouldReturnCorrectUserId() {
        // Given
        String username = "test@example.com";
        Long userId = 1L;
        String token = jwtUtil.generateToken(username, userId);

        // When
        Long extractedUserId = jwtUtil.extractUserId(token);

        // Then
        assertThat(extractedUserId).isEqualTo(userId);
    }

    @Test
    void validateToken_ShouldReturnTrue_WhenTokenIsValid() {
        // Given
        String username = "test@example.com";
        Long userId = 1L;
        String token = jwtUtil.generateToken(username, userId);

        // When
        Boolean isValid = jwtUtil.validateToken(token, username);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void validateToken_ShouldReturnFalse_WhenUsernameDoesNotMatch() {
        // Given
        String username = "test@example.com";
        String differentUsername = "different@example.com";
        Long userId = 1L;
        String token = jwtUtil.generateToken(username, userId);

        // When
        Boolean isValid = jwtUtil.validateToken(token, differentUsername);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void extractExpiration_ShouldReturnFutureDate() {
        // Given
        String username = "test@example.com";
        Long userId = 1L;
        String token = jwtUtil.generateToken(username, userId);

        // When
        var expiration = jwtUtil.extractExpiration(token);

        // Then
        assertThat(expiration).isAfter(new java.util.Date());
    }
}
