package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentWorkspaceResolverTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FreelanceRepository freelanceRepository;

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void currentFreelanceId_WithoutAuthentication_ShouldBeEmptyInPermitAllMode() {
        CurrentWorkspaceResolver resolver = new CurrentWorkspaceResolver(userRepository, freelanceRepository, true);

        assertThat(resolver.currentFreelanceId()).isEmpty();
    }

    @Test
    void currentFreelanceId_WithoutUserIdPrincipal_ShouldRequireAuthentication() {
        CurrentWorkspaceResolver resolver = new CurrentWorkspaceResolver(userRepository, freelanceRepository, false);
        authenticate("john.doe@example.com");

        assertThatThrownBy(resolver::currentFreelanceId)
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("Authentication required");
    }

    @Test
    void currentFreelanceId_WithoutWorkspace_ShouldBeDenied() {
        CurrentWorkspaceResolver resolver = new CurrentWorkspaceResolver(userRepository, freelanceRepository, false);
        authenticate(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(resolver::currentFreelanceId)
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("No workspace for the authenticated account");
    }

    @Test
    void currentFreelanceId_OutsideRequest_ShouldResolveThroughAccountEmail() {
        CurrentWorkspaceResolver resolver = new CurrentWorkspaceResolver(userRepository, freelanceRepository, false);
        authenticate(7L);
        stubWorkspace(7L, 3L);

        assertThat(resolver.currentFreelanceId()).contains(3L);
        assertThat(resolver.currentFreelanceId()).contains(3L);
        verify(userRepository, times(2)).findById(7L);
    }

    @Test
    void currentFreelanceId_WithinRequest_ShouldResolveOnce() {
        CurrentWorkspaceResolver resolver = new CurrentWorkspaceResolver(userRepository, freelanceRepository, false);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        authenticate(7L);
        stubWorkspace(7L, 3L);

        assertThat(resolver.currentFreelanceId()).contains(3L);
        assertThat(resolver.currentFreelanceId()).contains(3L);
        verify(userRepository, times(1)).findById(7L);
    }

    private void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private void stubWorkspace(Long userId, Long freelanceId) {
        User user = new User();
        user.setEmail("zaid@swirlit.dev");
        Freelance freelance = new Freelance();
        freelance.setId(freelanceId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(freelanceRepository.findByEmail("zaid@swirlit.dev")).thenReturn(Optional.of(freelance));
    }
}
