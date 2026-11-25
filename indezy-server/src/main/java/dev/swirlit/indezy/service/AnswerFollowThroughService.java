package dev.swirlit.indezy.service;

import dev.swirlit.indezy.ai.AiService;
import dev.swirlit.indezy.ai.ReplySortingAiService;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * What comes of an answer that came back through Indezy (see docs/features.md#emails-sent-from-indezy), when the
 * freelancer's AI is available: the answer sorted; an interested one opening an opportunity at Contact with the
 * company, the email and its answer in the opportunity's journal; a "not now" one setting the day to write back (the
 * date it gave, else three months later); a "do not contact" one marking the contact so.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerFollowThroughService {

    /** A "not now" giving no date is written back to this many months later. */
    static final int WRITE_BACK_MONTHS = 3;
    private static final Pattern REPLY_PREFIX = Pattern.compile("^(?i)((re|tr|fwd?|aw|wg)\\s*:\\s*)+");
    private static final int ROLE_MAX = 120;

    private final ReplySortingAiService sorting;
    private final AiService ai;
    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final ProjectService projects;
    private final ProjectRepository projectRepository;
    private final org.springframework.transaction.PlatformTransactionManager transactions;

    /** Sorts {@code answer} to {@code original}, read on {@code today}, and acts on it; without AI, or failing, nothing. */
    public void followThrough(ContactInteraction answer, ContactInteraction original, String subject, String text, LocalDate today) {
        Contact contact = answer.getContact();
        if (contact.getFreelance() == null || contact.getFreelance().getEmail() == null) {
            return;
        }
        users.findByEmail(contact.getFreelance().getEmail()).ifPresent(user -> {
            try {
                if (!ai.status(user.getId()).available()) {
                    return;
                }
                ReplySortingAiService.Sort sort = sorting.sort(user.getId(), subject, text, today);
                answer.setReplyKind(sort.kind());
                switch (sort.kind()) {
                    case DO_NOT_CONTACT -> contact.setDoNotContact(true);
                    case NOT_NOW -> answer.setWriteBackOn(sort.writeBackOn() == null ? today.plusMonths(WRITE_BACK_MONTHS) : sort.writeBackOn());
                    case INTERESTED -> opportunity(answer, original, subject);
                    default -> {
                        // No need, or anything else: the answer is only recorded.
                    }
                }
            } catch (RuntimeException e) {
                log.warn("The answer of contact {} could not be sorted: {}", contact.getId(), e.getMessage());
            }
        });
    }

    /** An opportunity at Contact with the contact's company, unless the email was already about one or no company is known. */
    private void opportunity(ContactInteraction answer, ContactInteraction original, String subject) {
        Contact contact = answer.getContact();
        if (answer.getProject() != null || contact.getClient() == null) {
            return;
        }
        ProjectDto project = new ProjectDto();
        project.setRole(role(subject));
        project.setStatus(ProjectStatus.CONTACT);
        project.setFreelanceId(contact.getFreelance().getId());
        project.setClientId(contact.getClient().getId());
        project.setDailyRate(freelances.findTargetDailyRate(contact.getFreelance().getId()).orElse(0));
        // In a transaction of its own: an opportunity that cannot be created leaves the answer recorded.
        org.springframework.transaction.support.TransactionTemplate isolated = new org.springframework.transaction.support.TransactionTemplate(transactions);
        isolated.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        ProjectDto created = isolated.execute(status -> projects.create(project));
        if (created == null) {
            return;
        }
        Project opened = projectRepository.getReferenceById(created.getId());
        answer.setProject(opened);
        original.setProject(opened);
        log.info("An interested answer of contact {} opened opportunity {}", contact.getId(), created.getId());
    }

    /** The answer's subject without its "Re:" prefixes, as the opportunity's role. */
    static String role(String subject) {
        String role = REPLY_PREFIX.matcher(subject == null ? "" : subject.strip()).replaceFirst("").strip();
        if (role.isEmpty()) {
            return "Opportunité";
        }
        return role.length() > ROLE_MAX ? role.substring(0, ROLE_MAX) : role;
    }
}
