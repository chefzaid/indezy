package dev.swirlit.indezy.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserIdReturnsPrincipalWhenAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(42L, null, List.of()));

        assertEquals(42L, SecurityUtils.getCurrentUserId());
    }

    @Test
    void getCurrentUserIdThrowsWhenNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertThrows(AuthenticationCredentialsNotFoundException.class,
                SecurityUtils::getCurrentUserId);
    }

    @Test
    void getCurrentUserIdThrowsWhenPrincipalIsNotUserId() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("john.doe@example.com", null, List.of()));

        assertThrows(AuthenticationCredentialsNotFoundException.class,
                SecurityUtils::getCurrentUserId);
    }
}
