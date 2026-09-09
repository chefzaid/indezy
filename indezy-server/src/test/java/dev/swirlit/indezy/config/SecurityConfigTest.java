package dev.swirlit.indezy.config;

import dev.swirlit.indezy.service.UserService;
import dev.swirlit.indezy.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"indezy.security.permit-all=false",
    "cors.allowed-origins=http://localhost:4200"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserService userService;

    @Test
    void cookiesAndExistingSessionsCannotAuthorizeWrites() throws Exception {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        mockMvc.perform(delete("/users/security/sessions/session-1")
                .session(session)
                .cookie(new Cookie("access_token", jwtUtil.generateToken("user@example.com", 1L))))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void bearerTokenAuthorizesWriteWithoutCsrfTokenOrSessionCookie() throws Exception {
        when(userService.terminateSession(1L, "session-1")).thenReturn(true);

        mockMvc.perform(delete("/users/security/sessions/session-1")
                .header("Authorization", "Bearer " + jwtUtil.generateToken("user@example.com", 1L)))
            .andExpect(status().isOk())
            .andExpect(content().string("true"))
            .andExpect(header().doesNotExist("Set-Cookie"));
        verify(userService).terminateSession(1L, "session-1");
    }

    @Test
    void configuredOriginCanPreflightUserUpdates() throws Exception {
        mockMvc.perform(options("/users/profile")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "PUT"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void untrustedOriginCannotPreflightUserUpdates() throws Exception {
        mockMvc.perform(options("/users/profile")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "PUT"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
