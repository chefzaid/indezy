package dev.swirlit.indezy.util;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Helper for reading the authenticated principal from the Spring Security context.
 * The principal is populated by {@code JwtAuthenticationFilter} with the user id
 * carried in the JWT.
 */
public final class SecurityUtils {

    /** The request attribute holding the id of the session the request's token belongs to. */
    public static final String SESSION_ATTRIBUTE = "indezy.sessionId";

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

    /** The id of the session the current request's token belongs to, or null outside an authenticated request. */
    public static Long getCurrentSessionId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes &&
                attributes.getRequest().getAttribute(SESSION_ATTRIBUTE) instanceof Long sessionId) {
            return sessionId;
        }
        return null;
    }
}
