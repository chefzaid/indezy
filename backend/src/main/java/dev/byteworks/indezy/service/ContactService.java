package dev.byteworks.indezy.service;

import dev.byteworks.indezy.dto.ContactDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.mapper.ContactMapper;
import dev.byteworks.indezy.model.Contact;
import dev.byteworks.indezy.model.Client;
import dev.byteworks.indezy.model.Freelance;
import dev.byteworks.indezy.repository.ContactRepository;
import dev.byteworks.indezy.repository.ClientRepository;
import dev.byteworks.indezy.repository.FreelanceRepository;
import dev.byteworks.indezy.constants.ErrorMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContactService {

    private final ContactRepository contactRepository;
    private final ClientRepository clientRepository;
    private final FreelanceRepository freelanceRepository;
    private final ContactMapper contactMapper;

    @Transactional(readOnly = true)
    public List<ContactDto> findAll() {
        log.debug("Finding all contacts");
        return contactRepository.findAll()
            .stream()
            .map(contactMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public ContactDto findById(Long id) {
        log.debug("Finding contact by id: {}", id);
        Contact contact = contactRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.CONTACT_NOT_FOUND, id)));
        return contactMapper.toDto(contact);
    }

    @Transactional(readOnly = true)
    public List<ContactDto> findByFreelanceId(Long freelanceId) {
        log.debug("Finding contacts by freelance id: {}", freelanceId);
        return contactRepository.findByFreelanceId(freelanceId)
            .stream()
            .map(contactMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ContactDto> findByClientId(Long clientId) {
        log.debug("Finding contacts by client id: {}", clientId);
        return contactRepository.findByClientId(clientId)
            .stream()
            .map(contactMapper::toDto)
            .toList();
    }

    public ContactDto create(ContactDto contactDto) {
        log.debug("Creating new contact: {} {}", contactDto.getFirstName(), contactDto.getLastName());
        
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
        
        Contact savedContact = contactRepository.save(contact);
        log.debug("Created contact with id: {}", savedContact.getId());
        
        return contactMapper.toDto(savedContact);
    }

    public ContactDto update(Long id, ContactDto contactDto) {
        log.debug("Updating contact with id: {}", id);
        
        Contact existingContact = contactRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.CONTACT_NOT_FOUND, id)));

        // Validate freelance exists if changed
        if (!existingContact.getFreelance().getId().equals(contactDto.getFreelanceId())) {
            Freelance freelance = freelanceRepository.findById(contactDto.getFreelanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Freelance not found with id: " + contactDto.getFreelanceId()));
            existingContact.setFreelance(freelance);
        }

        // Validate client exists if changed
        if (!existingContact.getClient().getId().equals(contactDto.getClientId())) {
            Client client = clientRepository.findById(contactDto.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + contactDto.getClientId()));
            existingContact.setClient(client);
        }

        contactMapper.updateEntity(contactDto, existingContact);
        Contact updatedContact = contactRepository.save(existingContact);
        
        log.info("Updated contact with id: {}", updatedContact.getId());
        return contactMapper.toDto(updatedContact);
    }

    public void delete(Long id) {
        log.debug("Deleting contact with id: {}", id);
        
        if (!contactRepository.existsById(id)) {
            throw new ResourceNotFoundException(String.format(ErrorMessages.CONTACT_NOT_FOUND, id));
        }

        contactRepository.deleteById(id);
        log.info("Deleted contact with id: {}", id);
    }

    @Transactional(readOnly = true)
    public List<ContactDto> searchByName(Long freelanceId, String name) {
        log.debug("Searching contacts by name: {} for freelance: {}", name, freelanceId);
        return contactRepository.findByFreelanceIdAndNameContaining(freelanceId, name)
            .stream()
            .map(contactMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ContactDto> searchByEmail(Long freelanceId, String email) {
        log.debug("Searching contacts by email: {} for freelance: {}", email, freelanceId);
        return contactRepository.findByFreelanceIdAndEmailContaining(freelanceId, email)
            .stream()
            .map(contactMapper::toDto)
            .toList();
    }
}
