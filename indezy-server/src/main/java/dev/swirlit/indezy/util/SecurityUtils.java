package dev.swirlit.indezy.util;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper for reading the authenticated principal from the Spring Security context.
 * The principal is populated by {@code JwtAuthenticationFilter} with the user id
 * carried in the JWT.
 */
public final class SecurityUtils {

    /** The request attribute holding the id of the session the request's token belongs to. */
    public static final String SESSION_ATTRIBUTE = "indezy.sessionId";
    /** The authority of a request authenticated by a personal API token rather than a signed-in session. */
    public static final String API_TOKEN_AUTHORITY = "API_TOKEN";
    /** Prefix of an API token's scopes among its authorities; a token without any reads the whole workspace. */
    public static final String API_SCOPE_PREFIX = "API_SCOPE_";

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

    /** Whether the current request was authenticated by a personal API token. */
    /** The scopes of the API token signing the request in, by name; empty when it has none (all of the workspace). */
    public static Set<String> apiTokenScopes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
            .filter(name -> name != null && name.startsWith(API_SCOPE_PREFIX)).map(name -> name.substring(API_SCOPE_PREFIX.length()))
            .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean isApiTokenRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> API_TOKEN_AUTHORITY.equals(authority.getAuthority()));
    }
}
