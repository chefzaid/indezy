package dev.byteworks.indezy.service;

import dev.byteworks.indezy.dto.ClientDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.mapper.ClientMapper;
import dev.byteworks.indezy.model.Client;
import dev.byteworks.indezy.model.Freelance;
import dev.byteworks.indezy.repository.ClientRepository;
import dev.byteworks.indezy.repository.FreelanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ClientService {

    private final ClientRepository clientRepository;
    private final FreelanceRepository freelanceRepository;
    private final ClientMapper clientMapper;

    @Transactional(readOnly = true)
    public List<ClientDto> findAll() {
        log.debug("Finding all clients");
        return clientRepository.findAll()
            .stream()
            .map(clientMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public ClientDto findById(Long id) {
        log.debug("Finding client by id: {}", id);
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));
        return clientMapper.toDto(client);
    }

    public ClientDto create(ClientDto clientDto) {
        log.debug("Creating new client: {}", clientDto.getCompanyName());
        
        // Validate freelance exists
        Freelance freelance = freelanceRepository.findById(clientDto.getFreelanceId())
            .orElseThrow(() -> new ResourceNotFoundException("Freelance not found with id: " + clientDto.getFreelanceId()));
        
        Client client = clientMapper.toEntity(clientDto);
        client.setId(null); // Ensure ID is null for creation
        client.setFreelance(freelance);
        
        Client savedClient = clientRepository.save(client);
        log.debug("Created client with id: {}", savedClient.getId());
        
        return clientMapper.toDto(savedClient);
    }

    public ClientDto update(Long id, ClientDto clientDto) {
        log.debug("Updating client with id: {}", id);
        
        Client existingClient = clientRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));
        
        // Validate freelance exists if changed
        if (!existingClient.getFreelance().getId().equals(clientDto.getFreelanceId())) {
            Freelance freelance = freelanceRepository.findById(clientDto.getFreelanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Freelance not found with id: " + clientDto.getFreelanceId()));
            existingClient.setFreelance(freelance);
        }
        
        clientMapper.updateEntity(clientDto, existingClient);
        
        Client updatedClient = clientRepository.save(existingClient);
        log.debug("Updated client with id: {}", updatedClient.getId());
        
        return clientMapper.toDto(updatedClient);
    }

    public void delete(Long id) {
        log.debug("Deleting client with id: {}", id);
        
        Client client = clientRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));
        
        clientRepository.delete(client);
        log.debug("Deleted client with id: {}", id);
    }

    @Transactional(readOnly = true)
    public List<ClientDto> findByFreelanceId(Long freelanceId) {
        log.debug("Finding clients by freelance id: {}", freelanceId);
        return clientRepository.findByFreelanceId(freelanceId)
            .stream()
            .map(clientMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ClientDto> findByFreelanceIdAndIsFinal(Long freelanceId, Boolean isFinal) {
        log.debug("Finding clients by freelance id: {} and isFinal: {}", freelanceId, isFinal);
        return clientRepository.findByFreelanceIdAndIsFinal(freelanceId, isFinal)
            .stream()
            .map(clientMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ClientDto> findByFreelanceIdAndCompanyNameContaining(Long freelanceId, String companyName) {
        log.debug("Finding clients by freelance id: {} and company name containing: {}", freelanceId, companyName);
        return clientRepository.findByFreelanceIdAndCompanyNameContaining(freelanceId, companyName)
            .stream()
            .map(clientMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ClientDto> findByFreelanceIdAndCity(Long freelanceId, String city) {
        log.debug("Finding clients by freelance id: {} and city: {}", freelanceId, city);
        return clientRepository.findByFreelanceIdAndCity(freelanceId, city)
            .stream()
            .map(clientMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public ClientDto findByIdWithProjects(Long id) {
        log.debug("Finding client by id with projects: {}", id);
        Client client = clientRepository.findByIdWithProjects(id)
            .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));
        return clientMapper.toDto(client);
    }

    @Transactional(readOnly = true)
    public ClientDto findByIdWithContacts(Long id) {
        log.debug("Finding client by id with contacts: {}", id);
        Client client = clientRepository.findByIdWithContacts(id)
            .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));
        return clientMapper.toDto(client);
    }

    @Transactional(readOnly = true)
    public List<String> findDistinctCitiesByFreelanceId(Long freelanceId) {
        log.debug("Finding distinct cities by freelance id: {}", freelanceId);
        return clientRepository.findDistinctCitiesByFreelanceId(freelanceId);
    }

    @Transactional(readOnly = true)
    public List<String> findDistinctDomainsByFreelanceId(Long freelanceId) {
        log.debug("Finding distinct domains by freelance id: {}", freelanceId);
        return clientRepository.findDistinctDomainsByFreelanceId(freelanceId);
    }
}
