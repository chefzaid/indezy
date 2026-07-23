package dev.swirlit.indezy.util;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Helper for reading the authenticated principal from the Spring Security context.
 * The principal is populated by {@code JwtAuthenticationFilter} with the user id
 * carried in the JWT.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Returns the id of the currently authenticated user.
     *
     * @return the authenticated user's id
     * @throws AuthenticationCredentialsNotFoundException if no authenticated user is present
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new AuthenticationCredentialsNotFoundException(
                "No authenticated user found in the security context");
    }
}
