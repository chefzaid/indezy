package dev.swirlit.indezy.service;

import dev.swirlit.indezy.ai.AiService;
import dev.swirlit.indezy.ai.InboxReadingAiService;
import dev.swirlit.indezy.dto.InterviewStepDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.MailProperties;
import dev.swirlit.indezy.mail.ReplyForwardComposer;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.AutopilotDraft;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.InboxEmail;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InboxEmailAction;
import dev.swirlit.indezy.model.enums.InboxEmailKind;
import dev.swirlit.indezy.model.enums.InteractionChannel;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.RefusedBy;
import dev.swirlit.indezy.model.enums.StepStatus;
import dev.swirlit.indezy.repository.AutopilotDraftRepository;
import dev.swirlit.indezy.repository.BookingPageRepository;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InboxEmailRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The inbox watcher (see docs/features.md#inbox-watcher): emails the member forwards to their own address
 * {@code inbox+<token>@<reply domain>}, read by their AI. An interview invite adds an Interview step to the
 * opportunity with the sender's company, with the slots it offers, moves it to Interview and drafts the answer; a
 * rejection moves it to Abandoned with the reason guessed; a question on availability or rate drafts the answer. Each
 * is recorded as an exchange of the sender, added to the contacts when unknown; Gmail's forwarding confirmation is
 * sent on to the member. The drafts wait on the dashboard a week.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxWatcherService {

    static final String LOCAL_PART = "inbox+";
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 20;
    private static final Set<ProjectStatus> OPEN = EnumSet.of(ProjectStatus.CONTACT, ProjectStatus.INTERVIEW, ProjectStatus.OFFER);
    private static final Pattern EMAIL = Pattern.compile("([\\w.+'-]+@[\\w-]+(?:\\.[\\w-]+)+)");
    /** The original sender of an email forwarded by hand: the first "From:" or "De :" line of the forwarded message. */
    private static final Pattern FORWARDED_FROM = Pattern.compile("(?im)^[>\\s*]*(?:from|de)\\s*:\\s*(.+)$");
    private static final Set<String> CONFIRMATIONS = Set.of("forwarding-noreply@google.com");
    private static final DateTimeFormatter SLOT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final ContactRepository contacts;
    private final ContactInteractionRepository interactions;
    private final ProjectRepository projects;
    private final ProjectService projectService;
    private final InterviewStepService steps;
    private final AutopilotDraftRepository drafts;
    private final InboxEmailRepository emails;
    private final InboxReadingAiService reading;
    private final AiService ai;
    private final BookingPageRepository bookingPages;
    private final MailProperties properties;
    private final ResendMailer mailer;
    private final ReplyForwardComposer composer;
    private final Clock clock;

    /** The address and what came through it lately; the address null until asked for, or while answers cannot be received. */
    public record Watcher(boolean available, String address, List<Handled> recent) {

        public Watcher {
            recent = List.copyOf(recent);
        }
    }

    /** A forwarded email and what was done with it. */
    public record Handled(Long id, String sender, String subject, InboxEmailKind kind, InboxEmailAction action, Long contactId,
                          Long projectId, LocalDateTime receivedAt) {
    }

    @Transactional(readOnly = true)
    public Watcher watcher(Long userId, Long freelanceId) {
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<Handled> recent = emails.findTop20ByFreelanceIdOrderByCreatedAtDescIdDesc(freelanceId).stream()
            .map(email -> new Handled(email.getId(), email.getSender(), email.getSubject(), email.getKind(), email.getAction(),
                email.getContactId(), email.getProjectId(), email.getCreatedAt())).toList();
        return new Watcher(properties.replies(), properties.replies() ? address(user.getInboxToken()) : null, recent);
    }

    /** A new address for the member, the previous one then receiving nothing. */
    @Transactional
    public Watcher renew(Long userId, Long freelanceId) {
        if (!properties.replies()) {
            return watcher(userId, freelanceId);
        }
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        user.setInboxToken(HexFormat.of().formatHex(bytes));
        users.save(user);
        return watcher(userId, freelanceId);
    }

    private String address(String token) {
        return token == null ? null : LOCAL_PART + token + "@" + properties.replyDomain().strip().toLowerCase(Locale.ROOT);
    }

    /** Whether this received email was already handled (a webhook delivered twice). */
    @Transactional(readOnly = true)
    public boolean seen(String emailId) {
        return emails.existsByEmailId(emailId);
    }

    /** Handles an email forwarded by {@code user}: {@code from} is the envelope's sender, {@code received} the email. */
    @Transactional
    public void receive(User user, String emailId, String from, ResendMailer.Received received) throws InterruptedException {
        Freelance workspace = freelances.findByEmail(user.getEmail()).orElse(null);
        if (workspace == null) {
            return;
        }
        String subject = Objects.toString(received.subject(), "").strip();
        String sender = sender(Objects.toString(received.from(), from), received.text(), user.getEmail());
        InboxEmail email = new InboxEmail();
        email.setFreelanceId(workspace.getId());
        email.setEmailId(emailId);
        email.setSender(cut(sender, InboxEmail.TEXT_MAX));
        email.setSubject(cut(subject, InboxEmail.TEXT_MAX));
        email.setKind(InboxEmailKind.OTHER);
        email.setAction(InboxEmailAction.NONE);
        if (sender != null && CONFIRMATIONS.contains(sender)) {
            email.setKind(InboxEmailKind.FORWARDING_CONFIRMATION);
            email.setAction(forwardConfirmation(user, emailId, sender, subject, received.text()) ? InboxEmailAction.FORWARDED : InboxEmailAction.NONE);
            emails.save(email);
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        if (ai.status(user.getId()).available()) {
            try {
                act(user, workspace, email, sender, subject, received.text(), today);
            } catch (RuntimeException e) {
                log.warn("A forwarded email of workspace {} could not be read: {}", workspace.getId(), e.getMessage());
            }
        }
        emails.save(email);
    }

    private void act(User user, Freelance workspace, InboxEmail email, String sender, String subject, String text, LocalDate today) {
        InboxReadingAiService.Read read = reading.read(user.getId(), subject, text, profile(user, workspace), today);
        email.setKind(read.kind());
        if (read.kind() == InboxEmailKind.OTHER || sender == null) {
            return;
        }
        Contact contact = contact(workspace, sender, read.kind());
        Project project = contact == null ? null : project(workspace, contact);
        email.setContactId(contact == null ? null : contact.getId());
        email.setProjectId(project == null ? null : project.getId());
        if (contact != null) {
            record(contact, project, subject, email.getEmailId(), today);
        }
        switch (read.kind()) {
            case INTERVIEW_INVITE -> {
                if (project != null) {
                    interview(project, read.slots(), user);
                    email.setAction(InboxEmailAction.INTERVIEW_STEP);
                }
                draft(workspace, contact, project, subject, read.answer(), email);
            }
            case REJECTION -> {
                if (project != null) {
                    projectService.updateStatus(project.getId(), ProjectStatus.LOST, RefusedBy.RECRUITER, read.reason());
                    email.setAction(InboxEmailAction.MARKED_LOST);
                }
            }
            case AVAILABILITY_ASK -> draft(workspace, contact, project, subject, read.answer(), email);
            default -> {
                // Nothing else to do.
            }
        }
    }

    private InboxReadingAiService.Profile profile(User user, Freelance workspace) {
        String link = bookingPages.findByFreelanceId(workspace.getId()).filter(dev.swirlit.indezy.model.BookingPage::isEnabled)
            .map(page -> properties.appUrl() + "/book/" + page.getSlug()).orElse(null);
        return new InboxReadingAiService.Profile(workspace.getFullName(), workspace.getAvailabilityDate(), user.getTargetDailyRate(), link);
    }

    /**
     * The sender's contact in the workspace; when unknown and the email asks something, a new contact with the company of
     * a contact sharing their email's domain, if any.
     */
    private Contact contact(Freelance workspace, String sender, InboxEmailKind kind) {
        List<Contact> all = contacts.findByFreelanceId(workspace.getId());
        Optional<Contact> known = all.stream().filter(contact -> contact.getEmail() != null && contact.getEmail().strip().equalsIgnoreCase(sender))
            .findFirst();
        if (known.isPresent()) {
            return known.get();
        }
        if (kind == InboxEmailKind.REJECTION) {
            return null;
        }
        String domain = sender.substring(sender.indexOf('@') + 1);
        Contact contact = new Contact();
        contact.setFreelance(workspace);
        String local = sender.substring(0, sender.indexOf('@'));
        contact.setFirstName(cut(local, InboxEmail.TEXT_MAX));
        contact.setEmail(sender);
        all.stream().filter(other -> other.getClient() != null && other.getEmail() != null &&
                other.getEmail().strip().toLowerCase(Locale.ROOT).endsWith("@" + domain))
            .findFirst().ifPresent(other -> contact.setClient(other.getClient()));
        return contacts.save(contact);
    }

    /** The open opportunity the contact last wrote about, else the latest changed one with their company. */
    private Project project(Freelance workspace, Contact contact) {
        Optional<Project> last = interactions.findByContactFreelanceIdOrderByContactIdAscOccurredOnDescIdDesc(workspace.getId()).stream()
            .filter(interaction -> interaction.getContact().getId().equals(contact.getId()) && interaction.getProject() != null &&
                open(interaction.getProject()))
            .map(ContactInteraction::getProject).findFirst();
        if (last.isPresent() || contact.getClient() == null) {
            return last.orElse(null);
        }
        return projects.findByClientId(contact.getClient().getId()).stream()
            .filter(project -> project.getFreelance() != null && workspace.getId().equals(project.getFreelance().getId()) && open(project))
            .max(Comparator.comparing(project -> Objects.requireNonNullElse(project.getUpdatedAt(), LocalDateTime.MIN))).orElse(null);
    }

    private static boolean open(Project project) {
        return OPEN.contains(project.getStatus()) && project.getArchivedAt() == null;
    }

    private void record(Contact contact, Project project, String subject, String emailId, LocalDate today) {
        ContactInteraction exchange = new ContactInteraction();
        exchange.setContact(contact);
        exchange.setProject(project);
        exchange.setOccurredOn(today);
        exchange.setChannel(InteractionChannel.EMAIL);
        exchange.setDirection(InteractionDirection.INCOMING);
        exchange.setSummary(cut(subject.isEmpty() ? "Email" : subject, ContactInteraction.SUMMARY_MAX));
        exchange.setInboundEmailId(emailId);
        interactions.save(exchange);
    }

    /** An Interview step: on the slot when only one is offered, to plan with the slots in its notes otherwise. */
    private void interview(Project project, List<LocalDateTime> slots, User user) {
        boolean english = "en".equalsIgnoreCase(user.getLanguagePreference());
        InterviewStepDto step = new InterviewStepDto();
        step.setProjectId(project.getId());
        step.setTitle(english ? "Interview" : "Entretien");
        step.setStatus(slots.size() == 1 ? StepStatus.PLANNED : StepStatus.TO_PLAN);
        step.setDate(slots.size() == 1 ? slots.getFirst() : null);
        if (slots.size() > 1) {
            step.setNotes((english ? "Proposed slots: " : "Créneaux proposés : ") +
                String.join(", ", slots.stream().map(SLOT::format).toList()));
        }
        steps.create(step);
        if (project.getStatus() == ProjectStatus.CONTACT) {
            projectService.updateStatus(project.getId(), ProjectStatus.INTERVIEW, null, null);
        }
    }

    private void draft(Freelance workspace, Contact contact, Project project, String subject, String answer, InboxEmail email) {
        if (contact == null || answer == null) {
            return;
        }
        AutopilotDraft draft = new AutopilotDraft();
        draft.setFreelanceId(workspace.getId());
        draft.setContactId(contact.getId());
        draft.setProjectId(project == null ? null : project.getId());
        String reply = subject.regionMatches(true, 0, "re:", 0, 3) ? subject : "Re: " + subject;
        draft.setSubject(cut(reply, AutopilotDraft.SUBJECT_MAX));
        draft.setBody(cut(answer, AutopilotDraft.BODY_MAX));
        drafts.save(draft);
        if (email.getAction() == InboxEmailAction.NONE) {
            email.setAction(InboxEmailAction.DRAFTED);
        }
    }

    private boolean forwardConfirmation(User user, String emailId, String sender, String subject, String text) throws InterruptedException {
        DocumentLanguage language = "en".equalsIgnoreCase(user.getLanguagePreference()) ? DocumentLanguage.EN : DocumentLanguage.FR;
        try {
            mailer.send(composer.compose(new ReplyForwardComposer.Answer(user.getEmail().strip(), "Gmail", sender, subject, text, language,
                "inbox-forward-" + emailId)));
            return true;
        } catch (MailException e) {
            log.warn("The forwarding confirmation {} could not be sent on: {}", emailId, e.getMessage());
            return false;
        }
    }

    /**
     * Who wrote the email, in lower case: the envelope's sender, or, when the member forwarded it by hand (the sender is
     * the member), the first "From:" or "De :" line of the forwarded message.
     */
    static String sender(String from, String text, String memberEmail) {
        String envelope = first(from);
        if (envelope != null && !envelope.equalsIgnoreCase(Objects.toString(memberEmail, "").strip())) {
            return envelope;
        }
        Matcher line = FORWARDED_FROM.matcher(Objects.toString(text, ""));
        while (line.find()) {
            String found = first(line.group(1));
            if (found != null && !found.equalsIgnoreCase(Objects.toString(memberEmail, "").strip())) {
                return found;
            }
        }
        return null;
    }

    private static String first(String value) {
        Matcher match = EMAIL.matcher(Objects.toString(value, ""));
        return match.find() ? match.group(1).toLowerCase(Locale.ROOT) : null;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
