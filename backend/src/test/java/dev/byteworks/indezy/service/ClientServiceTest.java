package dev.byteworks.indezy.service;

import dev.byteworks.indezy.dto.ClientDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.mapper.ClientMapper;
import dev.byteworks.indezy.model.Client;
import dev.byteworks.indezy.model.Freelance;
import dev.byteworks.indezy.model.enums.EmploymentStatus;
import dev.byteworks.indezy.repository.ClientRepository;
import dev.byteworks.indezy.repository.FreelanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private FreelanceRepository freelanceRepository;

    @Mock
    private ClientMapper clientMapper;

    @InjectMocks
    private ClientService clientService;

    private Client testClient;
    private ClientDto testClientDto;
    private Freelance testFreelance;

    @BeforeEach
    void setUp() {
        testFreelance = new Freelance();
        testFreelance.setId(1L);
        testFreelance.setFirstName("John");
        testFreelance.setLastName("Doe");
        testFreelance.setEmail("john.doe@example.com");
        testFreelance.setStatus(EmploymentStatus.FREELANCE);

        testClient = new Client();
        testClient.setId(1L);
        testClient.setCompanyName("Test Company");
        testClient.setAddress("123 Test St");
        testClient.setCity("Test City");
        testClient.setDomain("Technology");
        testClient.setIsFinal(true);
        testClient.setNotes("Test notes");
        testClient.setFreelance(testFreelance);

        testClientDto = new ClientDto();
        testClientDto.setId(1L);
        testClientDto.setCompanyName("Test Company");
        testClientDto.setAddress("123 Test St");
        testClientDto.setCity("Test City");
        testClientDto.setDomain("Technology");
        testClientDto.setIsFinal(true);
        testClientDto.setNotes("Test notes");
        testClientDto.setFreelanceId(1L);
    }

    @Test
    void findAll_ShouldReturnAllClients() {
        // Given
        List<Client> clients = Arrays.asList(testClient);
        when(clientRepository.findAll()).thenReturn(clients);
        when(clientMapper.toDto(testClient)).thenReturn(testClientDto);

        // When
        List<ClientDto> result = clientService.findAll();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCompanyName()).isEqualTo("Test Company");
        verify(clientRepository).findAll();
        verify(clientMapper).toDto(testClient);
    }

    @Test
    void findById_WithExistingId_ShouldReturnClient() {
        // Given
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(clientMapper.toDto(testClient)).thenReturn(testClientDto);

        // When
        ClientDto result = clientService.findById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCompanyName()).isEqualTo("Test Company");
        verify(clientRepository).findById(1L);
        verify(clientMapper).toDto(testClient);
    }

    @Test
    void findById_WithNonExistentId_ShouldThrowException() {
        // Given
        when(clientRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> clientService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Client not found with id: 999");

        verify(clientRepository).findById(999L);
        verify(clientMapper, never()).toDto(any());
    }

    @Test
    void create_WithValidData_ShouldCreateAndReturnClient() {
        // Given
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(clientMapper.toEntity(testClientDto)).thenReturn(testClient);
        when(clientRepository.save(testClient)).thenReturn(testClient);
        when(clientMapper.toDto(testClient)).thenReturn(testClientDto);

        // When
        ClientDto result = clientService.create(testClientDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCompanyName()).isEqualTo("Test Company");
        verify(freelanceRepository).findById(1L);
        verify(clientMapper).toEntity(testClientDto);
        verify(clientRepository).save(testClient);
        verify(clientMapper).toDto(testClient);
    }

    @Test
    void create_WithNonExistentFreelance_ShouldThrowException() {
        // Given
        when(freelanceRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> clientService.create(testClientDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Freelance not found with id: 1");

        verify(freelanceRepository).findById(1L);
        verify(clientRepository, never()).save(any());
    }

    @Test
    void update_WithValidData_ShouldUpdateAndReturnClient() {
        // Given
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(clientRepository.save(testClient)).thenReturn(testClient);
        when(clientMapper.toDto(testClient)).thenReturn(testClientDto);

        testClientDto.setCompanyName("Updated Company");

        // When
        ClientDto result = clientService.update(1L, testClientDto);

        // Then
        assertThat(result).isNotNull();
        verify(clientRepository).findById(1L);
        verify(clientRepository).save(testClient);
        verify(clientMapper).toDto(testClient);
    }

    @Test
    void update_WithNonExistentId_ShouldThrowException() {
        // Given
        when(clientRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> clientService.update(999L, testClientDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Client not found with id: 999");

        verify(clientRepository).findById(999L);
        verify(clientRepository, never()).save(any());
    }

    @Test
    void delete_WithExistingId_ShouldDeleteClient() {
        // Given
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        doNothing().when(clientRepository).delete(testClient);

        // When
        clientService.delete(1L);

        // Then
        verify(clientRepository).findById(1L);
        verify(clientRepository).delete(testClient);
    }

    @Test
    void delete_WithNonExistentId_ShouldThrowException() {
        // Given
        when(clientRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> clientService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Client not found with id: 999");

        verify(clientRepository).findById(999L);
        verify(clientRepository, never()).delete(any());
    }

    @Test
    void findByFreelanceId_ShouldReturnClientList() {
        // Given
        List<Client> clients = Arrays.asList(testClient);
        when(clientRepository.findByFreelanceId(1L)).thenReturn(clients);
        when(clientMapper.toDto(testClient)).thenReturn(testClientDto);

        // When
        List<ClientDto> result = clientService.findByFreelanceId(1L);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCompanyName()).isEqualTo("Test Company");
        verify(clientRepository).findByFreelanceId(1L);
        verify(clientMapper).toDto(testClient);
    }

    @Test
    void findByIdWithProjects_ShouldReturnClientWithProjects() {
        // Given
        when(clientRepository.findByIdWithProjects(1L)).thenReturn(Optional.of(testClient));
        when(clientMapper.toDto(testClient)).thenReturn(testClientDto);

        // When
        ClientDto result = clientService.findByIdWithProjects(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCompanyName()).isEqualTo("Test Company");
        verify(clientRepository).findByIdWithProjects(1L);
        verify(clientMapper).toDto(testClient);
    }

    @Test
    void findByIdWithContacts_ShouldReturnClientWithContacts() {
        // Given
        when(clientRepository.findByIdWithContacts(1L)).thenReturn(Optional.of(testClient));
        when(clientMapper.toDto(testClient)).thenReturn(testClientDto);

        // When
        ClientDto result = clientService.findByIdWithContacts(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCompanyName()).isEqualTo("Test Company");
        verify(clientRepository).findByIdWithContacts(1L);
        verify(clientMapper).toDto(testClient);
    }
}
