package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.BulkDeleteResult;
import dev.swirlit.indezy.dto.ContactDto;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.ContactMapper;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.constants.ErrorMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContactService {

    private static final int MAX_CHAIN = 1000;

    private final ContactRepository contactRepository;
    private final ClientRepository clientRepository;
    private final FreelanceRepository freelanceRepository;
    private final ContactMapper contactMapper;
    private final ContactInteractionService interactionService;

    @Transactional(readOnly = true)
    public ContactDto findById(Long id) {
        log.debug("Finding contact by id: {}", id);
        Contact contact = contactRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.CONTACT_NOT_FOUND, id)));
        return withLastContact(List.of(contact)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<ContactDto> findByFreelanceId(Long freelanceId) {
        log.debug("Finding contacts by freelance id: {}", freelanceId);
        return withLastContact(contactRepository.findByFreelanceId(freelanceId));
    }

    @Transactional(readOnly = true)
    public List<ContactDto> findByClientId(Long clientId) {
        log.debug("Finding contacts by client id: {}", clientId);
        return withLastContact(contactRepository.findByClientId(clientId));
    }

    public ContactDto create(ContactDto contactDto) {
        log.debug("Creating new contact");
        
        if (contactDto.getFreelanceId() == null || contactDto.getClientId() == null) {
            throw new IllegalArgumentException(ErrorMessages.CONTACT_OWNER_REQUIRED);
        }

        // Validate freelance exists
        Freelance freelance = freelanceRepository.findById(contactDto.getFreelanceId())
            .orElseThrow(() -> new ResourceNotFoundException("Freelance not found with id: " + contactDto.getFreelanceId()));
        
        // Validate client exists
        Client client = clientRepository.findById(contactDto.getClientId())
            .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + contactDto.getClientId()));
        
        Contact contact = contactMapper.toEntity(contactDto);
        contact.setId(null); // Ensure ID is null for creation
        contact.setFreelance(freelance);
        contact.setClient(client);
        contact.setManager(manager(contact, contactDto.getManagerId()));
        
        Contact savedContact = contactRepository.save(contact);
        log.debug("Created contact with id: {}", savedContact.getId());
        
        return contactMapper.toDto(savedContact);
    }

    public ContactDto update(Long id, ContactDto contactDto) {
        log.debug("Updating contact with id: {}", id);
        
        Contact existingContact = contactRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.CONTACT_NOT_FOUND, id)));

        // Validate freelance exists if changed (an omitted owner keeps the current one)
        if (contactDto.getFreelanceId() != null &&
                !existingContact.getFreelance().getId().equals(contactDto.getFreelanceId())) {
            Freelance freelance = freelanceRepository.findById(contactDto.getFreelanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Freelance not found with id: " + contactDto.getFreelanceId()));
            existingContact.setFreelance(freelance);
        }

        // Validate client exists if changed (an omitted client keeps the current one)
        if (contactDto.getClientId() != null &&
                !existingContact.getClient().getId().equals(contactDto.getClientId())) {
            Client client = clientRepository.findById(contactDto.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + contactDto.getClientId()));
            existingContact.setClient(client);
        }

        contactMapper.updateEntity(contactDto, existingContact);
        // The form always sends whom the contact reports to: none clears it.
        existingContact.setManager(manager(existingContact, contactDto.getManagerId()));
        Contact updatedContact = contactRepository.save(existingContact);
        
        log.info("Updated contact with id: {}", updatedContact.getId());
        return withLastContact(List.of(updatedContact)).getFirst();
    }

    public void delete(Long id) {
        log.debug("Deleting contact with id: {}", id);
        
        if (!contactRepository.existsById(id)) {
            throw new ResourceNotFoundException(String.format(ErrorMessages.CONTACT_NOT_FOUND, id));
        }

        contactRepository.clearManager(List.of(id));
        contactRepository.deleteById(id);
        log.info("Deleted contact with id: {}", id);
    }

    /** Deletes the contacts of {@code ids}, with their exchanges. */
    public BulkDeleteResult deleteAll(List<Long> ids) {
        contactRepository.clearManager(ids);
        List<Contact> contacts = contactRepository.findAllById(ids);
        contactRepository.deleteAll(contacts);
        log.info("Bulk deleted {} contacts", contacts.size());
        return new BulkDeleteResult(contacts.size(), 0);
    }

    /** The contact {@code managerId}, checked not to be {@code contact} nor anyone it manages, directly or not. */
    private Contact manager(Contact contact, Long managerId) {
        if (managerId == null) {
            return null;
        }
        if (managerId.equals(contact.getId())) {
            throw new FieldRejectedException("managerId", ErrorMessages.CONTACT_MANAGER_SELF);
        }
        Contact manager = contactRepository.findById(managerId)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.CONTACT_NOT_FOUND, managerId)));
        // A new contact manages nobody yet; the chain is bounded in case stored data already loops.
        int steps = 0;
        for (Contact above = manager; above != null && contact.getId() != null && steps < MAX_CHAIN; above = above.getManager()) {
            if (contact.getId().equals(above.getId())) {
                throw new FieldRejectedException("managerId", ErrorMessages.CONTACT_MANAGER_LOOP);
            }
            steps++;
        }
        return manager;
    }

    /** {@code contacts} as DTOs, each with the day of its latest exchange. */
    private List<ContactDto> withLastContact(List<Contact> contacts) {
        Map<Long, LocalDate> lastContacted = interactionService.lastContacted(contacts.stream().map(Contact::getId).toList());
        return contacts.stream().map(contact -> {
            ContactDto dto = contactMapper.toDto(contact);
            dto.setLastContactedOn(lastContacted.get(contact.getId()));
            return dto;
        }).toList();
    }
}
