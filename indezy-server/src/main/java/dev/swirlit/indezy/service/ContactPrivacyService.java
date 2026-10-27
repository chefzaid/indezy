package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ContactPersonalDataDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.ContactRelationRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A contact's requests about their own data (see docs/features.md#contact-privacy): what the workspace holds about them,
 * and its erasure. Each is recorded in the account's activity by the contact's id only, so the freelancer can show the
 * request was handled without keeping who made it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactPrivacyService {

    private final ContactRepository contacts;
    private final ContactInteractionRepository interactions;
    private final ContactRelationRepository relations;
    private final ContactService contactService;
    private final AccountEventService accountEvents;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ContactPersonalDataDto personalData(Long contactId, Long userId) {
        Contact contact = contacts.findById(contactId)
            .orElseThrow(() -> new ResourceNotFoundException("Contact not found: " + contactId));
        List<ContactPersonalDataDto.Exchange> exchanges = interactions.findByContactIdOrderByOccurredOnDescIdDesc(contactId).stream()
            .map(exchange -> new ContactPersonalDataDto.Exchange(exchange.getOccurredOn(), exchange.getChannel(), exchange.getDirection(),
                exchange.getReplied(), exchange.getSummary()))
            .toList();
        List<ContactPersonalDataDto.Link> links = relations.findInvolving(List.of(contactId)).stream()
            .map(relation -> {
                Contact other = relation.other(contactId);
                return new ContactPersonalDataDto.Link(other.getClient() == null ? null : other.getClient().getCompanyName(), relation.getNote());
            })
            .toList();
        accountEvents.record(userId, AccountEventType.CONTACT_DATA_EXPORTED, "contact " + contactId);
        return new ContactPersonalDataDto(keeper(contact.getFreelance()), LocalDate.now(clock), contact.getFirstName(), contact.getLastName(),
            contact.getEmail(), contact.getPhone(), contact.getClient() == null ? null : contact.getClient().getCompanyName(),
            contact.getNotes(), contact.getLegalBasis(), contact.getCreatedAt(), contact.getUpdatedAt(), exchanges, links);
    }

    /** Erases the contact on their request: the contact, its exchanges and links go, and the request is recorded. */
    @Transactional
    public void erase(Long contactId, Long userId) {
        contactService.delete(contactId);
        accountEvents.record(userId, AccountEventType.CONTACT_ERASED, "contact " + contactId);
        log.info("Contact {} erased on request", contactId);
    }

    private static String keeper(Freelance freelance) {
        return freelance == null ? null : Stream.of(freelance.getFirstName(), freelance.getLastName())
            .filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(" "));
    }
}
