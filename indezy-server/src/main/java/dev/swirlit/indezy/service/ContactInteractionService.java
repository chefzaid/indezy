package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.ContactInteractionDto;
import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.dto.ContactInteractionsDto;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.enums.InteractionChannel;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.ContactRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public void delete(Long contactId, Long id) {
        interactions.delete(find(contactId, id));
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
     * waiting for an answer {@code followUpDays} days or more after {@code today}'s, longest waiting first.
     */
    @Transactional(readOnly = true)
    public List<DashboardStatsDto.FollowUpDue> followUpsDue(Long freelanceId, int followUpDays, LocalDate today) {
        Map<Long, ContactInteraction> latest = new LinkedHashMap<>();
        // Newest first within each contact: the first one seen is its latest.
        interactions.findByContactFreelanceIdOrderByContactIdAscOccurredOnDescIdDesc(freelanceId)
            .forEach(interaction -> latest.putIfAbsent(interaction.getContact().getId(), interaction));
        return latest.values().stream()
            .filter(interaction -> interaction.getDirection() == InteractionDirection.OUTGOING && interaction.getReplied() == null)
            .map(interaction -> DashboardStatsDto.FollowUpDue.builder()
                .contactId(interaction.getContact().getId())
                .name(interaction.getContact().getFullName())
                .clientName(interaction.getContact().getClient() == null ? null : interaction.getContact().getClient().getCompanyName())
                .channel(interaction.getChannel())
                .sentOn(interaction.getOccurredOn())
                .daysWaiting(ChronoUnit.DAYS.between(interaction.getOccurredOn(), today))
                .build())
            .filter(due -> due.getDaysWaiting() >= followUpDays)
            .sorted(Comparator.comparingLong(DashboardStatsDto.FollowUpDue::getDaysWaiting).reversed())
            .toList();
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

    private static ContactInteractionDto toDto(ContactInteraction interaction) {
        return new ContactInteractionDto(interaction.getId(), interaction.getContact().getId(), interaction.getOccurredOn(),
            interaction.getChannel(), interaction.getDirection(), interaction.getReplied(), interaction.getSummary());
    }
}
