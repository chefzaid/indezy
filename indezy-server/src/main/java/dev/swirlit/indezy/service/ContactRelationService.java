package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.ContactRelationDto;
import dev.swirlit.indezy.dto.ContactRelationRequest;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.ContactRelation;
import dev.swirlit.indezy.repository.ContactRelationRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Who works with whom: links between contacts, often across a client and its ESN, up to {@value #MAX_RELATIONS} each. */
@Service
@RequiredArgsConstructor
@Transactional
public class ContactRelationService {

    static final int MAX_RELATIONS = 100;

    private final ContactRelationRepository relations;
    private final ContactRepository contacts;

    /** The people {@code contactId} works with, by name. */
    @Transactional(readOnly = true)
    public List<ContactRelationDto> list(Long contactId) {
        return toDtos(relations.findInvolving(List.of(contactId)), List.of(contactId));
    }

    /** The people the contacts of client {@code clientId} work with, for its org chart. */
    @Transactional(readOnly = true)
    public List<ContactRelationDto> listForClient(Long clientId) {
        List<Long> ids = contacts.findByClientId(clientId).stream().map(Contact::getId).toList();
        return ids.isEmpty() ? List.of() : toDtos(relations.findInvolving(ids), ids);
    }

    /** Links {@code contactId} to the other contact, or changes how they work together when they are linked already. */
    public ContactRelationDto link(Long contactId, ContactRelationRequest request) {
        Long otherId = request.otherId();
        if (otherId.equals(contactId)) {
            throw new FieldRejectedException("otherId", ErrorMessages.CONTACT_RELATION_SELF);
        }
        ContactRelation relation = find(contactId, otherId).orElseGet(() -> {
            if (relations.countInvolving(contactId) >= MAX_RELATIONS) {
                throw new InvalidStateException(String.format(ErrorMessages.CONTACT_RELATIONS_LIMIT, MAX_RELATIONS));
            }
            ContactRelation created = new ContactRelation();
            created.setFirst(contact(Math.min(contactId, otherId)));
            created.setSecond(contact(Math.max(contactId, otherId)));
            return created;
        });
        String note = request.note() == null ? "" : request.note().strip();
        relation.setNote(note.isEmpty() ? null : note);
        return toDto(relations.save(relation), contactId);
    }

    public void unlink(Long contactId, Long otherId) {
        relations.delete(find(contactId, otherId).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.CONTACT_RELATION_NOT_FOUND, otherId))));
    }

    /** Every link of workspace {@code freelanceId}, for the account data export. */
    @Transactional(readOnly = true)
    public List<ContactRelationDto> export(Long freelanceId) {
        return relations.findByFreelanceId(freelanceId).stream()
            .map(relation -> toDto(relation, relation.getFirst().getId())).toList();
    }

    private Optional<ContactRelation> find(Long contactId, Long otherId) {
        return relations.findByFirstIdAndSecondId(Math.min(contactId, otherId), Math.max(contactId, otherId));
    }

    private Contact contact(Long id) {
        return contacts.findById(id).orElseThrow(() ->
            new ResourceNotFoundException(String.format(ErrorMessages.CONTACT_NOT_FOUND, id)));
    }

    /** Each link seen from each of {@code ids} it involves, by contact then by the other's name. */
    private static List<ContactRelationDto> toDtos(List<ContactRelation> found, List<Long> ids) {
        List<ContactRelationDto> dtos = new ArrayList<>();
        for (ContactRelation relation : found) {
            for (Contact side : List.of(relation.getFirst(), relation.getSecond())) {
                if (ids.contains(side.getId())) {
                    dtos.add(toDto(relation, side.getId()));
                }
            }
        }
        dtos.sort(Comparator.comparing(ContactRelationDto::contactId)
            .thenComparing(ContactRelationDto::otherName, String.CASE_INSENSITIVE_ORDER));
        return dtos;
    }

    private static ContactRelationDto toDto(ContactRelation relation, Long contactId) {
        Contact other = relation.other(contactId);
        return new ContactRelationDto(contactId, other.getId(), other.getFullName(),
            other.getClient() == null ? null : other.getClient().getId(),
            other.getClient() == null ? null : other.getClient().getCompanyName(),
            other.getClient() == null ? null : other.getClient().getIsFinal(),
            relation.getNote());
    }
}
