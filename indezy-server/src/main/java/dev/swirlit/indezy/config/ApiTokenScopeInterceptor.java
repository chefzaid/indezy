package dev.swirlit.indezy.config;

import dev.swirlit.indezy.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;

import dev.swirlit.indezy.model.enums.ApiTokenScope;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps personal API tokens read-only: they read the workspace (GET), never write, and never reach the account, its
 * sessions and tokens, sign-in or the admin console. A token with scopes reads only the paths of those scopes
 * ({@link ApiTokenScope}); a path of no scope needs a token without any.
 */
@Configuration
public class ApiTokenScopeInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD");
    private static final List<String> ACCOUNT_PATHS = List.of("/users", "/admin", "/auth");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!SecurityUtils.isApiTokenRequest()) {
            return true;
        }
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        boolean account = ACCOUNT_PATHS.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
        if (account || !READ_METHODS.contains(request.getMethod())) {
            throw new AccessDeniedException("API tokens only read workspace data");
        }
        Set<String> scopes = SecurityUtils.apiTokenScopes();
        if (!scopes.isEmpty()) {
            Optional<ApiTokenScope> needed = ApiTokenScope.of(path);
            if (needed.isEmpty() || !scopes.contains(needed.get().name())) {
                throw new AccessDeniedException("This API token does not read " + needed.map(Enum::name).orElse("this path"));
            }
        }
        return true;
    }
}
