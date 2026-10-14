package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

/**
 * Resolves the caller's freelance workspace from the authenticated account (JWT principal)
 * through its email, once per request. Resolution is skipped only when
 * {@code indezy.security.permit-all} is enabled for tests that call endpoints anonymously.
 */
@Component
public class CurrentWorkspaceResolver {

    private static final String CURRENT_FREELANCE_ATTRIBUTE = CurrentWorkspaceResolver.class.getName() + ".freelanceId";

    private final UserRepository userRepository;
    private final FreelanceRepository freelanceRepository;
    private final boolean permitAll;

    public CurrentWorkspaceResolver(UserRepository userRepository,
                                    FreelanceRepository freelanceRepository,
                                    @Value("${indezy.security.permit-all:false}") boolean permitAll) {
        this.userRepository = userRepository;
        this.freelanceRepository = freelanceRepository;
        this.permitAll = permitAll;
    }

    /**
     * The caller's freelance workspace id, or empty when checks are disabled (permit-all test
     * mode without an authenticated account).
     */
    public Optional<Long> currentFreelanceId() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null &&
                request.getAttribute(CURRENT_FREELANCE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof Long cached) {
            return Optional.of(cached);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            if (permitAll) {
                return Optional.empty();
            }
            throw new AccessDeniedException("Authentication required");
        }

        Long freelanceId = userRepository.findById(userId)
            .flatMap(user -> freelanceRepository.findByEmail(user.getEmail()))
            .map(Freelance::getId)
            .orElseThrow(() -> new AccessDeniedException("No workspace for the authenticated account"));
        if (request != null) {
            request.setAttribute(CURRENT_FREELANCE_ATTRIBUTE, freelanceId, RequestAttributes.SCOPE_REQUEST);
        }
        return Optional.of(freelanceId);
    }
}
