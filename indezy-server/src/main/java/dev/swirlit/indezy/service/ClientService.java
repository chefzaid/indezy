package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.ClientDto;
import dev.swirlit.indezy.exception.ResourceInUseException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.ClientMapper;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
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

    private static final String CLIENT_NOT_FOUND = "Client not found with id: ";

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
            .orElseThrow(() -> new ResourceNotFoundException(CLIENT_NOT_FOUND + id));
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
            .orElseThrow(() -> new ResourceNotFoundException(CLIENT_NOT_FOUND + id));
        
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
            .orElseThrow(() -> new ResourceNotFoundException(CLIENT_NOT_FOUND + id));

        // The JPA mapping cascades removals to projects; refuse instead of silently deleting
        // every mission delivered for (or intermediated by) this client. Contacts still go with it.
        if (!client.getProjects().isEmpty() || !client.getMiddlemanProjects().isEmpty()) {
            throw new ResourceInUseException(String.format(ErrorMessages.CLIENT_HAS_PROJECTS, id));
        }
        
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

}
