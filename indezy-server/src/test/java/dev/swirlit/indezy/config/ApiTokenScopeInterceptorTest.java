package dev.swirlit.indezy.config;

import dev.swirlit.indezy.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiTokenScopeInterceptorTest {

    private final ApiTokenScopeInterceptor interceptor = new ApiTokenScopeInterceptor();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private boolean handles(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setContextPath("/api");
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void letsAnApiTokenReadTheWorkspaceOnly() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null,
            List.of(new SimpleGrantedAuthority(SecurityUtils.API_TOKEN_AUTHORITY))));

        assertThat(handles("GET", "/api/projects/by-freelance/3")).isTrue();
        assertThat(handles("HEAD", "/api/clients/4")).isTrue();
        assertThat(handles("GET", "/api/usersx")).isTrue();
        for (String[] refused : new String[][] {{"POST", "/api/clients"}, {"PUT", "/api/clients/4"}, {"GET", "/api/users/profile"},
            {"GET", "/api/%75sers/export"}, {"GET", "/api/admin/overview"}, {"GET", "/api/auth/sso"}}) {
            assertThatThrownBy(() -> handles(refused[0], refused[1])).as(refused[1]).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void leavesSessionsAlone() {
        assertThat(handles("POST", "/api/clients")).isTrue();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
        assertThat(handles("DELETE", "/api/users/sessions/3")).isTrue();
    }
}
