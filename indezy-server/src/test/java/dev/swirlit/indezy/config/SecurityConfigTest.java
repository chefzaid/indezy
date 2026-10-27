package dev.swirlit.indezy.config;

import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.UserService;
import dev.swirlit.indezy.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private UserService userService;

    /** A token authenticates only while its account exists and is active. */
    private long activeAccount() {
        return userRepository.findByEmail("security-config@example.com").orElseGet(() -> {
            User user = new User();
            user.setFirstName("Security");
            user.setLastName("Config");
            user.setEmail("security-config@example.com");
            user.setPasswordHash("not-a-real-hash");
            return userRepository.save(user);
        }).getId();
    }

    @Test
    void cookiesAndExistingSessionsCannotAuthorizeWrites() throws Exception {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        mockMvc.perform(post("/users/security/2fa/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}")
                .session(session)
                .cookie(new Cookie("access_token", jwtUtil.generateToken("user@example.com", 1L))))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void bearerTokenAuthorizesWriteWithoutCsrfTokenOrSessionCookie() throws Exception {
        long id = activeAccount();
        when(userService.disableTwoFactor(id, "123456")).thenReturn(true);

        mockMvc.perform(post("/users/security/2fa/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}")
                .header("Authorization", "Bearer " + jwtUtil.generateToken("security-config@example.com", id)))
            .andExpect(status().isOk())
            .andExpect(content().string("true"))
            .andExpect(header().doesNotExist("Set-Cookie"));
        verify(userService).disableTwoFactor(id, "123456");
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
    void configuredOriginCanPreflightKanbanStatusChanges() throws Exception {
        // Status, favorite and interview-step transitions are PATCH endpoints.
        mockMvc.perform(options("/projects/1/status")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "PATCH"))
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
