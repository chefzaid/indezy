package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.enums.DossierFormat;
import dev.swirlit.indezy.dto.CvProfileDtos;
import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.ContactInteractionDto;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.exception.ServiceUnavailableException;
import dev.swirlit.indezy.mail.ContactEmailComposer;
import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DossierLayout;
import dev.swirlit.indezy.model.enums.InteractionChannel;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.util.SkillNames;
import org.springframework.beans.factory.annotation.Autowired;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Emails a contact for the freelancer through Indezy (see docs/features.md#emails-sent-from-indezy), recorded as an
 * exchange from them so the follow-ups due count it, and with open tracking only when they ask for it on that email: the
 * email then says so, and a one-pixel image tells when it is opened.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactEmailService {

    /** At most this many emails go from Indezy for a workspace in 24 hours. */
    public static final int DAILY_MAX = 50;
    public static final int SUBJECT_MAX = 200;
    public static final int BODY_MAX = 20_000;
    /** Opens seen this soon after sending are a security scanner's, not the recipient's. */
    static final Duration SCAN_DELAY = Duration.ofSeconds(30);
    /** Opens stop being counted this long after sending. */
    static final Duration TRACKED_FOR = Duration.ofDays(30);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ResendMailer mailer;
    private final ContactEmailComposer composer;
    private final ContactRepository contacts;
    private final ContactInteractionRepository interactions;
    private final ProjectRepository projects;
    private final FreelanceRepository freelances;
    private final DossierService dossiers;
    private final Clock clock;

    /**
     * What to send: about an opportunity or not, with open tracking or not, with a copy to the sender or not, with the
     * dossier de compétences attached or not.
     */
    public record Message(Long projectId, String subject, String body, boolean track, boolean copy, DocumentLanguage language,
                          boolean dossier, Long campaignId, Long cvProfileId) {

        public Message(Long projectId, String subject, String body, boolean track, boolean copy, DocumentLanguage language) {
            this(projectId, subject, body, track, copy, language, false, null, null);
        }

        public Message(Long projectId, String subject, String body, boolean track, boolean copy, DocumentLanguage language, boolean dossier) {
            this(projectId, subject, body, track, copy, language, dossier, null, null);
        }

        // Spring MVC logs bodies through toString at DEBUG: never the subject or the message.
        @Override
        public String toString() {
            return "Message[projectId=" + projectId + ", track=" + track + "]";
        }
    }

    /**
     * A contact of an ESN the workspace worked with or heard from, who can be emailed: how many times they answered or
     * wrote, and when they were last in touch; what their company posted lately; and what earlier campaigns got from
     * them: how many wrote to them without an answer, and how many opportunities opened with their company after one did.
     */
    public record Recipient(Long id, String firstName, String name, String company, String email, int answers, LocalDate lastContactedOn,
                            int postings, String mission, int silentCampaigns, int campaignOpportunities, Integer ratePaid,
                            LocalDate missionEndsOn, boolean related) {
    }

    /** What a company posted for the profile lately: how many offers, and the newest one's title. */
    record Postings(int count, String latest) {
    }

    /** Offers their company published this long ago at most count as missions it is hiring for. */
    static final int POSTINGS_DAYS = 60;

    /** A mission won through a company and ending within this many days ranks its contacts first: it will want the next one. */
    static final int ENDING_DAYS = 84;

    private JobOfferRepository offers;

    private dev.swirlit.indezy.mail.MailProperties mailProperties;

    /** Where answers come back to; without it, they go straight to the freelancer. */
    @Autowired(required = false)
    void setMailProperties(dev.swirlit.indezy.mail.MailProperties properties) {
        this.mailProperties = properties;
    }

    private CvProfileService cvProfiles;

    /** The CV profiles, to attach the closest one; without them, the dossier is. */
    @Autowired(required = false)
    void setCvProfiles(CvProfileService service) {
        this.cvProfiles = service;
    }

    /** The feed's offers, to find the companies posting missions for the profile; without them, none is. */
    @Autowired(required = false)
    void setOffers(JobOfferRepository repository) {
        this.offers = repository;
    }

    /** Whoever was in touch more recently than this is left out of an announcement, to not write again so soon. */
    static final int RECENT_DAYS = 42;

    /** Whether email can go from Indezy, and how many went for the workspace in the last 24 hours. */
    public record Status(boolean available, long sentToday, int dailyMax) {
    }

    @Transactional(readOnly = true)
    public Status status(Long freelanceId) {
        return new Status(mailer.configured(), interactions.countByContactFreelanceIdAndEmailSentAtAfter(freelanceId,
            clock.instant().minus(Duration.ofDays(1))), DAILY_MAX);
    }

    /**
     * Whom to tell of an availability: the contacts with an address at the ESNs, not blacklisted, leaving out anyone in
     * touch in the last {@value #RECENT_DAYS} days; those at a company whose mission won ends within {@value #ENDING_DAYS}
     * days first, then those at an ESN the workspace had an opportunity with (as the ESN or the client), whose contacts
     * answered it before or that posted missions for the profile ({@code related}), then the others; within each, those
     * who answered most first, then by the campaigns' results, company and name.
     */
    @Transactional(readOnly = true)
    public List<Recipient> availabilityRecipients(Long freelanceId) {
        Set<Long> workedWith = new HashSet<>();
        List<Project> pipeline = projects.findByFreelanceId(freelanceId);
        pipeline.forEach(project -> Stream.of(project.getClient(), project.getMiddleman())
            .filter(Objects::nonNull).forEach(company -> workedWith.add(company.getId())));
        // The best daily rate each company paid on a mission won through it, as the ESN or the client.
        Map<Long, Integer> ratePaid = new HashMap<>();
        pipeline.stream().filter(project -> project.getStatus() == dev.swirlit.indezy.model.enums.ProjectStatus.WON && project.getDailyRate() != null)
            .forEach(project -> Stream.of(project.getClient(), project.getMiddleman()).filter(Objects::nonNull)
                .forEach(company -> ratePaid.merge(company.getId(), project.getDailyRate(), Math::max)));
        // The nearest last day, within ENDING_DAYS, of a mission won through each company, as the ESN or the client.
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        Map<Long, LocalDate> endsOn = new HashMap<>();
        pipeline.stream().filter(project -> project.getStatus() == dev.swirlit.indezy.model.enums.ProjectStatus.WON)
            .filter(project -> project.lastDay() != null && !project.lastDay().isBefore(today) && !project.lastDay().isAfter(today.plusDays(ENDING_DAYS)))
            .forEach(project -> Stream.of(project.getClient(), project.getMiddleman()).filter(Objects::nonNull)
                .forEach(company -> endsOn.merge(company.getId(), project.lastDay(), (one, other) -> one.isBefore(other) ? one : other)));
        Map<Long, int[]> answers = new HashMap<>();
        Map<Long, LocalDate> lastInTouch = new HashMap<>();
        Set<Long> answeringCompanies = new HashSet<>();
        Map<Long, List<ContactInteraction>> exchanges = new HashMap<>();
        for (ContactInteraction interaction : interactions.findByContactFreelanceIdOrderByContactIdAscOccurredOnDescIdDesc(freelanceId)) {
            Contact contact = interaction.getContact();
            exchanges.computeIfAbsent(contact.getId(), id -> new ArrayList<>()).add(interaction);
            lastInTouch.merge(contact.getId(), interaction.getOccurredOn(), (one, other) -> one.isAfter(other) ? one : other);
            if (interaction.getDirection() == InteractionDirection.INCOMING || Boolean.TRUE.equals(interaction.getReplied())) {
                answers.computeIfAbsent(contact.getId(), id -> new int[1])[0]++;
                if (contact.getClient() != null) {
                    answeringCompanies.add(contact.getClient().getId());
                }
            }
        }
        LocalDate recent = today.minusDays(RECENT_DAYS);
        List<Contact> reachable = contacts.findByFreelanceId(freelanceId).stream()
            .filter(contact -> contact.getEmail() != null && !contact.getEmail().isBlank() && contact.getAnonymizedAt() == null)
            .filter(contact -> !Boolean.TRUE.equals(contact.getDoNotContact()))
            .filter(contact -> contact.getClient() != null && !Boolean.TRUE.equals(contact.getClient().getIsFinal()) &&
                !Boolean.TRUE.equals(contact.getClient().getIsBlacklisted()))
            .filter(contact -> lastInTouch.get(contact.getId()) == null || lastInTouch.get(contact.getId()).isBefore(recent))
            .toList();
        Map<String, Postings> postings = postings(freelanceId, reachable);
        Postings none = new Postings(0, null);
        Map<Long, Integer> silent = new HashMap<>();
        Map<Long, Integer> opportunities = campaignOpportunities(exchanges, pipeline, silent);
        // Every ESN contact is a recruiter or an account manager: those at a company with no tie yet come last.
        java.util.function.Predicate<Contact> related = contact -> workedWith.contains(contact.getClient().getId()) ||
            answeringCompanies.contains(contact.getClient().getId()) || postings.containsKey(JobOffer.companyKeyOf(contact.getClient().getCompanyName()));
        return reachable.stream()
            .map(contact -> new Recipient(contact.getId(), contact.getFirstName(), contact.getFullName(), contact.getClient().getCompanyName(),
                contact.getEmail().strip(), answers.getOrDefault(contact.getId(), new int[1])[0], lastInTouch.get(contact.getId()),
                postings.getOrDefault(JobOffer.companyKeyOf(contact.getClient().getCompanyName()), none).count(),
                postings.getOrDefault(JobOffer.companyKeyOf(contact.getClient().getCompanyName()), none).latest(),
                silent.getOrDefault(contact.getId(), 0), opportunities.getOrDefault(contact.getClient().getId(), 0),
                ratePaid.get(contact.getClient().getId()), endsOn.get(contact.getClient().getId()), related.test(contact)))
            .sorted(Comparator.comparing((Recipient recipient) -> recipient.missionEndsOn() == null)
                .thenComparing(Recipient::related, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingInt(Recipient::answers).reversed())
                .thenComparing(Comparator.comparingInt(Recipient::campaignOpportunities).reversed())
                .thenComparingInt(Recipient::silentCampaigns)
                .thenComparing(Comparator.comparingInt(Recipient::postings).reversed())
                .thenComparing(Comparator.comparingInt((Recipient recipient) -> recipient.ratePaid() == null ? 0 : recipient.ratePaid()).reversed())
                .thenComparing(Recipient::company, String.CASE_INSENSITIVE_ORDER).thenComparing(Recipient::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /**
     * What the earlier campaigns got: into {@code silent}, how many campaigns wrote to each contact without an answer
     * ({@link ProspectionCampaignService#answered}); returned, how many opportunities opened with each company (as the
     * client or the ESN) on or after the day a campaign first wrote to one of its contacts, by company id.
     */
    private static Map<Long, Integer> campaignOpportunities(Map<Long, List<ContactInteraction>> exchanges, List<Project> pipeline,
                                                            Map<Long, Integer> silent) {
        Map<Long, LocalDate> firstWritten = new HashMap<>();
        exchanges.forEach((contactId, history) -> {
            Set<Long> unanswered = new HashSet<>();
            for (ContactInteraction email : history) {
                if (email.getCampaignId() == null || email.getDirection() == InteractionDirection.INCOMING) {
                    continue;
                }
                if (email.getContact().getClient() != null) {
                    firstWritten.merge(email.getContact().getClient().getId(), email.getOccurredOn(),
                        (one, other) -> one.isBefore(other) ? one : other);
                }
                if (!ProspectionCampaignService.answered(email, history)) {
                    unanswered.add(email.getCampaignId());
                }
            }
            if (!unanswered.isEmpty()) {
                silent.put(contactId, unanswered.size());
            }
        });
        Map<Long, Integer> opened = new HashMap<>();
        if (firstWritten.isEmpty()) {
            return opened;
        }
        for (Project project : pipeline) {
            if (project.getCreatedAt() == null) {
                continue;
            }
            LocalDate created = project.getCreatedAt().toLocalDate();
            Stream.of(project.getClient(), project.getMiddleman()).filter(Objects::nonNull).map(Client::getId).distinct()
                .filter(id -> firstWritten.containsKey(id) && !created.isBefore(firstWritten.get(id)))
                .forEach(id -> opened.merge(id, 1, Integer::sum));
        }
        return opened;
    }

    /**
     * How many offers each of these contacts' companies published in the feed in the last {@value #POSTINGS_DAYS} days
     * naming one of the profile's skills, by {@link JobOffer#companyKeyOf} name; companies with none are absent.
     */
    private Map<String, Postings> postings(Long freelanceId, List<Contact> reachable) {
        Set<String> keys = new HashSet<>();
        reachable.forEach(contact -> {
            String key = JobOffer.companyKeyOf(contact.getClient().getCompanyName());
            if (key != null) {
                keys.add(key);
            }
        });
        Set<String> skills = new HashSet<>();
        freelances.findProfileSkills(freelanceId).forEach(skill -> skills.add(SkillNames.key(SkillNames.canonical(skill))));
        skills.remove("");
        if (offers == null || keys.isEmpty() || skills.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<Long>> matching = new HashMap<>();
        Map<String, Object[]> newest = new HashMap<>();
        for (Object[] row : offers.findPublishersSkills(keys, clock.instant().minus(Duration.ofDays(POSTINGS_DAYS)))) {
            if (row[2] instanceof String skill && skills.contains(SkillNames.key(SkillNames.canonical(skill)))) {
                String key = (String) row[1];
                matching.computeIfAbsent(key, ignored -> new HashSet<>()).add((Long) row[0]);
                newest.merge(key, row, (kept, other) -> ((Instant) other[4]).isAfter((Instant) kept[4]) ? other : kept);
            }
        }
        Map<String, Postings> counts = new HashMap<>();
        matching.forEach((key, ids) -> counts.put(key, new Postings(ids.size(), (String) newest.get(key)[3])));
        return counts;
    }

    /** The skill keys the contact's company asked for in the offers it published in the last {@value #POSTINGS_DAYS} days. */
    private Set<String> asked(Contact contact) {
        String key = contact.getClient() == null ? null : JobOffer.companyKeyOf(contact.getClient().getCompanyName());
        if (offers == null || key == null) {
            return Set.of();
        }
        Set<String> skills = new HashSet<>();
        for (Object[] row : offers.findPublishersSkills(Set.of(key), clock.instant().minus(Duration.ofDays(POSTINGS_DAYS)))) {
            if (row[2] instanceof String skill) {
                skills.add(SkillNames.key(SkillNames.canonical(skill)));
            }
        }
        skills.remove("");
        return skills;
    }

    @Transactional
    public ContactInteractionDto send(Long freelanceId, Long contactId, Message message) {
        return send(null, freelanceId, contactId, message);
    }

    /** Sends the message, with the dossier de compétences of {@code userId}'s profile attached when asked. */
    @Transactional
    public ContactInteractionDto send(Long userId, Long freelanceId, Long contactId, Message message) {
        if (!mailer.configured()) {
            throw new InvalidStateException("Email cannot be sent from Indezy yet");
        }
        Contact contact = contacts.findById(contactId).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.CONTACT_NOT_FOUND, contactId)));
        if (contact.getEmail() == null || contact.getEmail().isBlank()) {
            throw new FieldRejectedException("contactId", "The contact has no email address");
        }
        Freelance freelance = freelances.findById(freelanceId).orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        Instant now = clock.instant();
        if (interactions.countByContactFreelanceIdAndEmailSentAtAfter(freelanceId, now.minus(Duration.ofDays(1))) >= DAILY_MAX) {
            throw new InvalidStateException("Up to " + DAILY_MAX + " emails a day go from Indezy: send this one from your mail app");
        }
        if (interactions.countByContactId(contactId) >= ContactInteractionService.MAX_INTERACTIONS) {
            throw new InvalidStateException(String.format(ErrorMessages.CONTACT_INTERACTIONS_LIMIT, ContactInteractionService.MAX_INTERACTIONS));
        }
        String subject = message.subject().strip();
        ContactInteraction interaction = new ContactInteraction();
        interaction.setContact(contact);
        interaction.setOccurredOn(LocalDate.now(clock.withZone(PARIS)));
        interaction.setChannel(InteractionChannel.EMAIL);
        interaction.setDirection(InteractionDirection.OUTGOING);
        interaction.setSummary(subject.length() > ContactInteraction.SUMMARY_MAX ? subject.substring(0, ContactInteraction.SUMMARY_MAX) : subject);
        interaction.setProject(message.projectId() == null ? null : projects.findById(message.projectId()).orElse(null));
        interaction.setEmailSentAt(now);
        interaction.setEmailToken(message.track() ? token() : null);
        interaction.setEmailOpens(0);
        interaction.setCampaignId(message.campaignId());
        interaction.setObjectionToken(message.campaignId() == null ? null : token());
        // Answers come back through Indezy when a reply address is set up: the exchange is marked answered, the answer forwarded.
        boolean replies = mailProperties != null && mailProperties.replies();
        interaction.setReplyToken(replies ? replyToken() : null);
        interactions.save(interaction);

        String sender = Stream.of(freelance.getFirstName(), freelance.getLastName()).filter(Objects::nonNull).map(String::strip)
            .filter(part -> !part.isEmpty()).collect(Collectors.joining(" "));
        String name = sender.isEmpty() ? "Indezy" : sender;
        DocumentLanguage language = message.language() == null ? DocumentLanguage.FR : message.language();
        String key = "contact-email-" + UUID.randomUUID();
        String replyTo = replies ? "reply+" + interaction.getReplyToken() + "@" + mailProperties.replyDomain().strip() : freelance.getEmail();
        Email email = composer.compose(contact.getEmail().strip(), subject, message.body(), new ContactEmailComposer.Sender(name,
            replyTo), language, new ContactEmailComposer.Delivery(interaction.getEmailToken(), key, interaction.getObjectionToken()));
        if (message.dossier() && userId != null) {
            ActivityReportService.PdfFile dossier = attached(userId, freelanceId, contact, message.projectId(), message.cvProfileId(), language);
            email = email.withAttachments(List.of(new Email.Attachment(dossier.fileName(), "application/pdf", dossier.content())));
        }
        try {
            mailer.send(email);
        } catch (MailException e) {
            // Nothing is recorded: the transaction rolls back with this exception.
            throw new ServiceUnavailableException("The email could not be sent: try again in a moment, or send it from your mail app", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("The email could not be sent: try again in a moment, or send it from your mail app", e);
        }
        log.info("Sent email {} of workspace {} through Indezy, tracked: {}", interaction.getId(), freelanceId, message.track());
        if (message.copy() && freelance.getEmail() != null) {
            sendCopy(freelance.getEmail(), contact.getFullName(), subject, message.body(), language, key + "-copy");
        }
        return ContactInteractionService.toDto(interaction);
    }

    /**
     * The CV attached to an email: the CV profile chosen, else the one closest to what the contact's company asked for in
     * the missions it posted lately and the opportunity's stack (in the email's language, its twin when it has one), else
     * the dossier focused on those skills.
     */
    private ActivityReportService.PdfFile attached(Long userId, Long freelanceId, Contact contact, Long projectId, Long cvProfileId,
                                                   DocumentLanguage language) {
        Set<String> asked = askedFor(contact, projectId);
        Long chosen = cvProfileId;
        if (chosen == null && cvProfiles != null) {
            chosen = cvProfiles.closest(userId, freelanceId, asked).map(CvProfileDtos.Profile::id).orElse(null);
        }
        if (chosen != null && cvProfiles != null) {
            try {
                return cvProfiles.file(userId, freelanceId, chosen, DossierLayout.NAMED, language, DossierFormat.PDF);
            } catch (ResourceNotFoundException e) {
                // Deleted since it was chosen, or not the workspace's: the closest one goes instead.
                Long closest = cvProfiles.closest(userId, freelanceId, asked).map(CvProfileDtos.Profile::id).orElse(null);
                if (closest != null) {
                    return cvProfiles.file(userId, freelanceId, closest, DossierLayout.NAMED, language, DossierFormat.PDF);
                }
            }
        }
        return dossiers.render(DossierService.focused(dossiers.dossier(userId, freelanceId, DossierLayout.NAMED), asked), language);
    }

    /** The skills a contact's company asked for lately, and the opportunity's stack when the email is about one. */
    private Set<String> askedFor(Contact contact, Long projectId) {
        Set<String> asked = new HashSet<>(asked(contact));
        if (projectId != null) {
            projects.findById(projectId).map(Project::getTechStack).ifPresent(stack -> Stream.of(stack.split("[,;/|]")).map(String::strip)
                .filter(part -> !part.isEmpty()).map(part -> SkillNames.key(SkillNames.canonical(part))).forEach(asked::add));
        }
        asked.remove("");
        return asked;
    }

    /** The CV profile an email to the contact would carry, about the opportunity or not; none without a profile. */
    @Transactional(readOnly = true)
    public Optional<CvProfileDtos.Profile> cvProfileFor(Long userId, Long freelanceId, Long contactId, Long projectId) {
        Contact contact = contacts.findById(contactId).orElseThrow(() -> new ResourceNotFoundException("Contact not found"));
        return cvProfiles == null ? Optional.empty() : cvProfiles.closest(userId, freelanceId, askedFor(contact, projectId));
    }

    /** Counts an open of the email whose tracking image holds {@code token}, unless too early or too late to mean one. */
    @Transactional
    public void opened(String token) {
        Instant now = clock.instant();
        interactions.findByEmailToken(token).filter(interaction -> interaction.getEmailSentAt() != null)
            .filter(interaction -> !now.isBefore(interaction.getEmailSentAt().plus(SCAN_DELAY)))
            .filter(interaction -> now.isBefore(interaction.getEmailSentAt().plus(TRACKED_FOR)))
            .ifPresent(interaction -> {
                interaction.setEmailOpens((interaction.getEmailOpens() == null ? 0 : interaction.getEmailOpens()) + 1);
                if (interaction.getEmailOpenedAt() == null) {
                    interaction.setEmailOpenedAt(now);
                }
            });
    }

    /** The contact an objection link names, null for none: whose email it was, and who sent it. */
    public record Objection(String contactName, String senderName) {
    }

    /** Who an objection link is about, without changing anything; empty for an unknown link. */
    @Transactional(readOnly = true)
    public java.util.Optional<Objection> objection(String token) {
        return interactions.findByObjectionToken(token).map(interaction -> new Objection(interaction.getContact().getFullName(),
            senderOf(interaction.getContact())));
    }

    /**
     * The contact objects to being contacted again: marked **Asked not to be contacted**, which keeps them out of every
     * list of recipients, and the objection recorded in their exchanges; false for an unknown link. Objecting twice
     * changes nothing more.
     */
    @Transactional
    public boolean object(String token) {
        java.util.Optional<ContactInteraction> found = interactions.findByObjectionToken(token);
        if (found.isEmpty()) {
            return false;
        }
        Contact contact = found.get().getContact();
        if (!Boolean.TRUE.equals(contact.getDoNotContact())) {
            contact.setDoNotContact(true);
            ContactInteraction objection = new ContactInteraction();
            objection.setContact(contact);
            objection.setOccurredOn(LocalDate.now(clock.withZone(PARIS)));
            objection.setChannel(InteractionChannel.EMAIL);
            objection.setDirection(InteractionDirection.INCOMING);
            objection.setSummary("Asked not to be contacted again (link in a prospecting email)");
            interactions.save(objection);
        }
        return true;
    }

    private String senderOf(Contact contact) {
        Freelance freelance = contact.getFreelance();
        String name = freelance == null ? "" : Stream.of(freelance.getFirstName(), freelance.getLastName()).filter(Objects::nonNull)
            .map(String::strip).filter(part -> !part.isEmpty()).collect(Collectors.joining(" "));
        return name.isEmpty() ? "Indezy" : name;
    }

    private void sendCopy(String to, String recipientName, String subject, String body, DocumentLanguage language, String key) {
        try {
            mailer.send(composer.copy(to, recipientName, subject, body, language, key));
        } catch (MailException e) {
            log.warn("The copy of an email sent through Indezy did not go: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 160 random bits in lower-case hex: a reply address's local part stays under 64 characters and survives lower-casing. */
    private static String replyToken() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
