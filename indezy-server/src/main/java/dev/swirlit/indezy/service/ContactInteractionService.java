package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.AnswerSortDto;
import dev.swirlit.indezy.dto.ContactInteractionDto;
import dev.swirlit.indezy.dto.ContactInteractionsDto;
import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.InteractionChannel;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import dev.swirlit.indezy.model.enums.ReplyKind;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** The exchanges kept with a contact, up to {@value #MAX_INTERACTIONS} each, and what they say about it. */
@Service
@RequiredArgsConstructor
@Transactional
public class ContactInteractionService {

    static final int MAX_INTERACTIONS = 500;
    // Dates are the freelancer's days; a day ahead of Paris covers any time zone they may be in.
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final ContactInteractionRepository interactions;
    private final ContactRepository contacts;
    private final ProjectRepository projects;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ContactInteractionsDto list(Long contactId) {
        return summarize(interactions.findByContactIdOrderByOccurredOnDescIdDesc(contactId));
    }

    public ContactInteractionDto create(Long contactId, ContactInteractionDto request) {
        if (interactions.countByContactId(contactId) >= MAX_INTERACTIONS) {
            throw new InvalidStateException(String.format(ErrorMessages.CONTACT_INTERACTIONS_LIMIT, MAX_INTERACTIONS));
        }
        ContactInteraction interaction = new ContactInteraction();
        interaction.setContact(contacts.findById(contactId).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.CONTACT_NOT_FOUND, contactId))));
        return toDto(interactions.save(fill(interaction, request)));
    }

    public ContactInteractionDto update(Long contactId, Long id, ContactInteractionDto request) {
        return toDto(fill(find(contactId, id), request));
    }

    /**
     * Sorts answer {@code id} as the freelancer says, the AI having maybe got it wrong: a "not now" one written back to on
     * the day given, else three months later; the contact marked not to be contacted when it says so, and no longer
     * when it said so before and no longer does.
     */
    public ContactInteractionDto sortAnswer(Long contactId, Long id, AnswerSortDto request) {
        ContactInteraction answer = find(contactId, id);
        if (answer.getDirection() != InteractionDirection.INCOMING) {
            throw new FieldRejectedException("replyKind", ErrorMessages.CONTACT_INTERACTION_NOT_ANSWER);
        }
        ReplyKind before = answer.getReplyKind();
        answer.setReplyKind(request.replyKind());
        LocalDate later = LocalDate.now(clock.withZone(PARIS)).plusMonths(AnswerFollowThroughService.WRITE_BACK_MONTHS);
        answer.setWriteBackOn(request.replyKind() != ReplyKind.NOT_NOW ? null : Objects.requireNonNullElse(request.writeBackOn(), later));
        if (request.replyKind() == ReplyKind.DO_NOT_CONTACT) {
            answer.getContact().setDoNotContact(true);
        } else if (before == ReplyKind.DO_NOT_CONTACT) {
            answer.getContact().setDoNotContact(false);
        }
        return toDto(answer);
    }

    public void delete(Long contactId, Long id) {
        interactions.delete(find(contactId, id));
    }

    /** The exchanges about opportunity {@code projectId}, with whom, newest first. */
    @Transactional(readOnly = true)
    public List<ContactInteractionDto> listForProject(Long projectId) {
        return interactions.findByProjectIdOrderByOccurredOnDescIdDesc(projectId).stream().map(ContactInteractionService::toDto).toList();
    }

    /** The exchanges with {@code contactIds} recorded after {@code since}, by contact. */
    @Transactional(readOnly = true)
    public Map<Long, List<ContactInteraction>> recordedSince(Collection<Long> contactIds, java.time.LocalDateTime since) {
        if (contactIds.isEmpty()) {
            return Map.of();
        }
        return interactions.findByContactIdInAndCreatedAtAfter(contactIds, since).stream()
            .collect(Collectors.groupingBy(interaction -> interaction.getContact().getId()));
    }

    /** The day of the latest exchange of each of {@code contactIds} that has one. */
    @Transactional(readOnly = true)
    public Map<Long, LocalDate> lastContacted(Collection<Long> contactIds) {
        if (contactIds.isEmpty()) {
            return Map.of();
        }
        return toMap(interactions.findLastByContactIds(contactIds));
    }

    /** The day of the latest exchange of each contact of workspace {@code freelanceId} that has one. */
    @Transactional(readOnly = true)
    public Map<Long, LocalDate> lastContactedInWorkspace(Long freelanceId) {
        return toMap(interactions.findLastByFreelanceId(freelanceId));
    }

    /**
     * The contacts of workspace {@code freelanceId} whose latest exchange is a message the freelancer sent, still
     * waiting for an answer {@code followUpDays} days or more on {@code today} (two days past their company's usual answer
     * time once known, see {@link #usualAnswerDays(Long)}), longest waiting first; and those whose latest exchange is a
     * "not now" answer whose day to write back has come ({@code writeBackOn}); none who asked not to be contacted.
     */
    @Transactional(readOnly = true)
    public List<DashboardStatsDto.FollowUpDue> followUpsDue(Long freelanceId, int followUpDays, LocalDate today) {
        Map<Long, ContactInteraction> latest = new LinkedHashMap<>();
        List<ContactInteraction> all = interactions.findByContactFreelanceIdOrderByContactIdAscOccurredOnDescIdDesc(freelanceId);
        Map<Long, Integer> usual = usualAnswerDays(all);
        // Newest first within each contact: the first one seen is its latest.
        all.forEach(interaction -> latest.putIfAbsent(interaction.getContact().getId(), interaction));
        return latest.values().stream()
            // Never someone who asked not to be contacted, nor whose data was anonymized.
            .filter(interaction -> reachable(interaction.getContact()))
            .filter(interaction -> waiting(interaction) || writeBackDue(interaction, today))
            .map(interaction -> DashboardStatsDto.FollowUpDue.builder()
                .contactId(interaction.getContact().getId())
                .name(interaction.getContact().getFullName())
                .clientName(interaction.getContact().getClient() == null ? null : interaction.getContact().getClient().getCompanyName())
                .channel(interaction.getChannel())
                .sentOn(interaction.getOccurredOn())
                .daysWaiting(ChronoUnit.DAYS.between(interaction.getOccurredOn(), today))
                .usualAnswerDays(interaction.getContact().getClient() == null ? null : usual.get(interaction.getContact().getClient().getId()))
                .projectId(interaction.getProject() == null ? null : interaction.getProject().getId())
                .writeBackOn(interaction.getWriteBackOn())
                .build())
            // Timed from the company's usual answer time when known: two days' grace after it, else the workspace's delay.
            .filter(due -> due.getWriteBackOn() != null ||
                due.getDaysWaiting() >= (due.getUsualAnswerDays() == null ? followUpDays : due.getUsualAnswerDays() + ANSWER_GRACE_DAYS))
            .sorted(Comparator.comparingLong(DashboardStatsDto.FollowUpDue::getDaysWaiting).reversed())
            .toList();
    }

    private static boolean reachable(Contact contact) {
        return !Boolean.TRUE.equals(contact.getDoNotContact()) && contact.getAnonymizedAt() == null;
    }

    private static boolean waiting(ContactInteraction interaction) {
        return interaction.getDirection() == InteractionDirection.OUTGOING && interaction.getReplied() == null;
    }

    private static boolean writeBackDue(ContactInteraction interaction, LocalDate today) {
        return interaction.getDirection() == InteractionDirection.INCOMING && interaction.getWriteBackOn() != null &&
            !interaction.getWriteBackOn().isAfter(today);
    }

    /** Stages to update on the workspace's open opportunities, from what their contacts recently said. */
    @Transactional(readOnly = true)
    public List<DashboardStatsDto.StatusSuggestion> statusSuggestions(Long freelanceId, LocalDate today) {
        return StatusSuggestions.build(interactions.findByContactFreelanceIdOrderByContactIdAscOccurredOnDescIdDesc(freelanceId), today);
    }

    /** A company that never answered is flagged once this many messages to its contacts went unanswered. */
    public static final int UNANSWERED_MIN = 3;

    /**
     * The companies whose contacts never answered the freelancer, by company name: those sent at least
     * {@value #UNANSWERED_MIN} messages, with none answered nor any exchange started by their side; the messages' count.
     */
    public static Map<String, Integer> unansweredCompanies(List<ContactInteraction> all) {
        Map<Long, int[]> byCompany = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (ContactInteraction interaction : all) {
            Client company = interaction.getContact() == null ? null : interaction.getContact().getClient();
            if (company == null || company.getId() == null || company.getCompanyName() == null) {
                continue;
            }
            names.put(company.getId(), company.getCompanyName());
            int[] counts = byCompany.computeIfAbsent(company.getId(), id -> new int[2]);
            if (interaction.getDirection() == InteractionDirection.INCOMING || Boolean.TRUE.equals(interaction.getReplied())) {
                counts[1]++;
            } else {
                counts[0]++;
            }
        }
        Map<String, Integer> silent = new HashMap<>();
        byCompany.forEach((id, counts) -> {
            if (counts[1] == 0 && counts[0] >= UNANSWERED_MIN) {
                silent.merge(names.get(id), counts[0], Integer::sum);
            }
        });
        return silent;
    }

    /** A company's answers need this many to say how long it usually takes. */
    static final int ANSWERS_MIN = 2;
    /** A follow-up is due this many days after a company's usual answer time. */
    static final int ANSWER_GRACE_DAYS = 2;

    /**
     * How many days each company of workspace {@code freelanceId} usually takes to answer, by company id: for each message
     * the freelancer sent one of its contacts, the days until that contact's next exchange started by them, the median of
     * at least {@value #ANSWERS_MIN} such answers.
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> usualAnswerDays(Long freelanceId) {
        return usualAnswerDays(interactions.findByContactFreelanceIdOrderByContactIdAscOccurredOnDescIdDesc(freelanceId));
    }

    static Map<Long, Integer> usualAnswerDays(List<ContactInteraction> byContactNewestFirst) {
        Map<Long, List<Long>> delays = new HashMap<>();
        Map<Long, List<ContactInteraction>> byContact = new LinkedHashMap<>();
        byContactNewestFirst.forEach(interaction -> byContact.computeIfAbsent(interaction.getContact().getId(), id -> new ArrayList<>())
            .add(interaction));
        for (List<ContactInteraction> exchanges : byContact.values()) {
            Long companyId = exchanges.getFirst().getContact().getClient() == null ? null : exchanges.getFirst().getContact().getClient().getId();
            if (companyId == null) {
                continue;
            }
            // Oldest first: each message sent waits for the next exchange the contact starts.
            LocalDate sent = null;
            for (int index = exchanges.size() - 1; index >= 0; index--) {
                ContactInteraction exchange = exchanges.get(index);
                if (exchange.getDirection() == InteractionDirection.OUTGOING) {
                    sent = sent == null ? exchange.getOccurredOn() : sent;
                } else if (sent != null) {
                    delays.computeIfAbsent(companyId, id -> new ArrayList<>()).add(ChronoUnit.DAYS.between(sent, exchange.getOccurredOn()));
                    sent = null;
                }
            }
        }
        Map<Long, Integer> usual = new HashMap<>();
        delays.forEach((companyId, days) -> {
            if (days.size() >= ANSWERS_MIN) {
                List<Long> sorted = days.stream().sorted().toList();
                int middle = sorted.size() / 2;
                long median = sorted.size() % 2 == 1 ? sorted.get(middle) : Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2.0);
                usual.put(companyId, (int) median);
            }
        });
        return usual;
    }

    /** Every exchange of workspace {@code freelanceId}, by contact, for the account data export. */
    @Transactional(readOnly = true)
    public List<ContactInteractionDto> export(Long freelanceId) {
        return interactions.findByContactFreelanceIdOrderByContactIdAscOccurredOnDescIdDesc(freelanceId).stream()
            .map(ContactInteractionService::toDto).toList();
    }

    /** {@code newestFirst} with the latest day, the response rate and the preferred channel read from it. */
    static ContactInteractionsDto summarize(List<ContactInteraction> newestFirst) {
        int asked = 0;
        int answered = 0;
        // The channels the contact answered or wrote first on, newest first, and how often.
        List<InteractionChannel> engagedOn = new ArrayList<>();
        Map<InteractionChannel, Integer> engaged = new EnumMap<>(InteractionChannel.class);
        for (ContactInteraction interaction : newestFirst) {
            Boolean replied = interaction.getReplied();
            if (interaction.getDirection() == InteractionDirection.OUTGOING) {
                if (replied == null) {
                    continue;
                }
                asked++;
                if (!replied) {
                    continue;
                }
                answered++;
            }
            engagedOn.add(interaction.getChannel());
            engaged.merge(interaction.getChannel(), 1, Integer::sum);
        }
        InteractionChannel preferred = null;
        for (InteractionChannel channel : engagedOn) {
            // Newest first, so a tie keeps the channel used last.
            if (preferred == null || engaged.get(channel) > engaged.get(preferred)) {
                preferred = channel;
            }
        }
        return new ContactInteractionsDto(
            newestFirst.stream().map(ContactInteractionService::toDto).toList(),
            newestFirst.isEmpty() ? null : newestFirst.getFirst().getOccurredOn(),
            asked,
            answered,
            asked == 0 ? null : Math.round(answered * 100f / asked),
            preferred);
    }

    private ContactInteraction fill(ContactInteraction interaction, ContactInteractionDto request) {
        if (request.occurredOn().isAfter(LocalDate.now(clock.withZone(PARIS)).plusDays(1))) {
            throw new FieldRejectedException("occurredOn", ErrorMessages.CONTACT_INTERACTION_IN_FUTURE);
        }
        interaction.setOccurredOn(request.occurredOn());
        interaction.setChannel(request.channel());
        interaction.setDirection(request.direction());
        // Only an exchange the freelancer started waits for an answer.
        interaction.setReplied(request.direction() == InteractionDirection.OUTGOING ? request.replied() : null);
        String summary = request.summary() == null ? "" : request.summary().strip();
        interaction.setSummary(summary.isEmpty() ? null : summary);
        interaction.setProject(request.projectId() == null ? null : projects.findById(request.projectId()).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, request.projectId()))));
        return interaction;
    }

    private ContactInteraction find(Long contactId, Long id) {
        return interactions.findByIdAndContactId(id, contactId).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.CONTACT_INTERACTION_NOT_FOUND, id)));
    }

    private static Map<Long, LocalDate> toMap(List<ContactInteractionRepository.LastInteraction> rows) {
        return rows.stream().collect(Collectors.toMap(
            ContactInteractionRepository.LastInteraction::getContactId, ContactInteractionRepository.LastInteraction::getLastOn));
    }

    static ContactInteractionDto toDto(ContactInteraction interaction) {
        Project project = interaction.getProject();
        return new ContactInteractionDto(interaction.getId(), interaction.getContact().getId(), interaction.getContact().getFullName(),
            interaction.getOccurredOn(), interaction.getChannel(), interaction.getDirection(), interaction.getReplied(),
            interaction.getSummary(), project == null ? null : project.getId(), project == null ? null : project.getRole(),
            interaction.getEmailSentAt() == null ? null : new ContactInteractionDto.SentEmail(interaction.getEmailSentAt(),
                interaction.getEmailToken() != null, interaction.getEmailOpenedAt(),
                interaction.getEmailOpens() == null ? 0 : interaction.getEmailOpens()), interaction.getReplyKind(),
            interaction.getWriteBackOn());
    }
}
