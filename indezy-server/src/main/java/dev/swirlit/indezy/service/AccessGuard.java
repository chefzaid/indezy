package dev.swirlit.indezy.service;

import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SeasonRepository;
import dev.swirlit.indezy.repository.SourceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.function.Function;

/**
 * Resource ownership checks: every freelance workspace (projects, clients, contacts, sources,
 * interview steps, seasons) is visible only to the account it belongs to. The workspace of the caller is
 * resolved from the authenticated account (JWT principal) through its email.
 *
 * <p>Resources of another workspace are reported as not found so their existence is not
 * revealed; an explicit foreign workspace id is rejected with 403. Checks are skipped only when
 * {@code indezy.security.permit-all} is enabled for tests that call endpoints anonymously.
 */
@Component
public class AccessGuard {

    private static final String CURRENT_FREELANCE_ATTRIBUTE = AccessGuard.class.getName() + ".freelanceId";

    private final UserRepository userRepository;
    private final FreelanceRepository freelanceRepository;
    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final ContactRepository contactRepository;
    private final SourceRepository sourceRepository;
    private final InterviewStepRepository interviewStepRepository;
    private final SeasonRepository seasonRepository;
    private final boolean permitAll;

    @SuppressWarnings("java:S107") // one repository per guarded aggregate
    public AccessGuard(UserRepository userRepository,
                       FreelanceRepository freelanceRepository,
                       ProjectRepository projectRepository,
                       ClientRepository clientRepository,
                       ContactRepository contactRepository,
                       SourceRepository sourceRepository,
                       InterviewStepRepository interviewStepRepository,
                       SeasonRepository seasonRepository,
                       @Value("${indezy.security.permit-all:false}") boolean permitAll) {
        this.userRepository = userRepository;
        this.freelanceRepository = freelanceRepository;
        this.projectRepository = projectRepository;
        this.clientRepository = clientRepository;
        this.contactRepository = contactRepository;
        this.sourceRepository = sourceRepository;
        this.interviewStepRepository = interviewStepRepository;
        this.seasonRepository = seasonRepository;
        this.permitAll = permitAll;
    }

    /**
     * The caller's freelance workspace id, or empty when checks are disabled (permit-all test
     * mode without an authenticated account).
     */
    public Optional<Long> currentFreelanceId() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null
                && request.getAttribute(CURRENT_FREELANCE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof Long cached) {
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

    /** The workspace a create request should use: the caller's own, whatever the payload says. */
    public Long resolveFreelanceId(Long requested) {
        return currentFreelanceId()
            .map(current -> {
                if (requested != null && !requested.equals(current)) {
                    throw new AccessDeniedException("Access denied to resource");
                }
                return current;
            })
            .orElse(requested);
    }

    /** Rejects a request addressed to another workspace. */
    public void requireFreelance(Long freelanceId) {
        currentFreelanceId().ifPresent(current -> {
            if (!current.equals(freelanceId)) {
                throw new AccessDeniedException("Access denied to resource");
            }
        });
    }

    public void requireProject(Long projectId) {
        requireOwned(projectId, projectRepository::findOwnerFreelanceIdById, "Project");
    }

    public void requireClient(Long clientId) {
        requireOwned(clientId, clientRepository::findOwnerFreelanceIdById, "Client");
    }

    /** Checks an optional client reference (e.g. a project's intermediary). */
    public void requireClientIfPresent(Long clientId) {
        if (clientId != null) {
            requireClient(clientId);
        }
    }

    public void requireContact(Long contactId) {
        requireOwned(contactId, contactRepository::findOwnerFreelanceIdById, "Contact");
    }

    public void requireSource(Long sourceId) {
        requireOwned(sourceId, sourceRepository::findOwnerFreelanceIdById, "Source");
    }

    public void requireSourceIfPresent(Long sourceId) {
        if (sourceId != null) {
            requireSource(sourceId);
        }
    }

    public void requireInterviewStep(Long stepId) {
        requireOwned(stepId, interviewStepRepository::findOwnerFreelanceIdById, "Interview step");
    }

    public void requireSeason(Long seasonId) {
        requireOwned(seasonId, seasonRepository::findOwnerFreelanceIdById, "Season");
    }

    public void requireSeasonIfPresent(Long seasonId) {
        if (seasonId != null) {
            requireSeason(seasonId);
        }
    }

    private void requireOwned(Long id, Function<Long, Optional<Long>> ownerLookup, String label) {
        currentFreelanceId().ifPresent(current -> {
            if (id == null) {
                throw new ResourceNotFoundException(label + " not found");
            }
            Long owner = ownerLookup.apply(id)
                .orElseThrow(() -> new ResourceNotFoundException(label + " not found with id: " + id));
            if (!owner.equals(current)) {
                // Same answer as a missing resource so ids of other workspaces cannot be probed.
                throw new ResourceNotFoundException(label + " not found with id: " + id);
            }
        });
    }
}
