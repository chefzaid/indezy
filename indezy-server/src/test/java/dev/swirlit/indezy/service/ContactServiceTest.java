package dev.swirlit.indezy.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.swirlit.indezy.dto.ContactDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.ContactMapper;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private FreelanceRepository freelanceRepository;

    @Mock
    private ContactMapper contactMapper;

    @Mock
    private ContactInteractionService contactInteractionService;

    @InjectMocks
    private ContactService contactService;

    private Freelance testFreelance;
    private Client testClient;
    private Contact testContact;
    private ContactDto testContactDto;

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
        testClient.setFreelance(testFreelance);

        testContact = new Contact();
        testContact.setId(1L);
        testContact.setFirstName("Jane");
        testContact.setLastName("Smith");
        testContact.setEmail("jane.smith@example.com");
        testContact.setPhone("0123456789");
        testContact.setFreelance(testFreelance);
        testContact.setClient(testClient);

        testContactDto = new ContactDto();
        testContactDto.setId(1L);
        testContactDto.setFirstName("Jane");
        testContactDto.setLastName("Smith");
        testContactDto.setEmail("jane.smith@example.com");
        testContactDto.setPhone("0123456789");
        testContactDto.setFreelanceId(1L);
        testContactDto.setClientId(1L);
    }

    @Test
    void findAll_ShouldReturnAllContacts() {
        when(contactRepository.findAll()).thenReturn(List.of(testContact));
        when(contactMapper.toDto(testContact)).thenReturn(testContactDto);

        List<ContactDto> result = contactService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("Jane");
        verify(contactRepository).findAll();
    }

    @Test
    void findById_WithExistingId_ShouldReturnContact() {
        when(contactRepository.findById(1L)).thenReturn(Optional.of(testContact));
        when(contactMapper.toDto(testContact)).thenReturn(testContactDto);

        ContactDto result = contactService.findById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        verify(contactRepository).findById(1L);
    }

    @Test
    void findById_WithNonExistingId_ShouldThrowResourceNotFoundException() {
        when(contactRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contactService.findById(999L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByFreelanceId_ShouldReturnContactsForFreelance() {
        when(contactRepository.findByFreelanceId(1L)).thenReturn(List.of(testContact));
        when(contactMapper.toDto(testContact)).thenReturn(testContactDto);

        List<ContactDto> result = contactService.findByFreelanceId(1L);

        assertThat(result).hasSize(1);
        verify(contactRepository).findByFreelanceId(1L);
    }

    @Test
    void findByClientId_ShouldReturnContactsForClient() {
        when(contactRepository.findByClientId(1L)).thenReturn(List.of(testContact));
        when(contactMapper.toDto(testContact)).thenReturn(testContactDto);

        List<ContactDto> result = contactService.findByClientId(1L);

        assertThat(result).hasSize(1);
        verify(contactRepository).findByClientId(1L);
    }

    @Test
    void create_WithValidData_ShouldCreateContactAndLogItsIdOnly() {
        Logger logger = (Logger) LoggerFactory.getLogger(ContactService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        Level level = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(contactMapper.toEntity(testContactDto)).thenReturn(testContact);
        when(contactRepository.save(any(Contact.class))).thenReturn(testContact);
        when(contactMapper.toDto(testContact)).thenReturn(testContactDto);

        ContactDto result;
        try {
            result = contactService.create(testContactDto);
        } finally {
            logger.detachAppender(logs);
            logger.setLevel(level);
        }

        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("Jane");
        verify(contactRepository).save(any(Contact.class));
        // Its debug lines name the contact by its id, never by its name.
        List<String> lines = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(lines).contains("Creating new contact").anyMatch(line -> line.startsWith("Created contact with id: "));
        assertThat(lines).noneMatch(line -> line.contains("Jane") || line.contains("Smith"));
    }

    @Test
    void create_WithInvalidFreelanceId_ShouldThrowResourceNotFoundException() {
        when(freelanceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contactService.create(testContactDto))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Freelance not found");
        verify(contactRepository, never()).save(any());
    }

    @Test
    void create_WithInvalidClientId_ShouldThrowResourceNotFoundException() {
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));
        when(clientRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contactService.create(testContactDto))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Client not found");
        verify(contactRepository, never()).save(any());
    }

    @Test
    void update_WithValidData_ShouldUpdateContact() {
        when(contactRepository.findById(1L)).thenReturn(Optional.of(testContact));
        when(contactRepository.save(testContact)).thenReturn(testContact);
        when(contactMapper.toDto(testContact)).thenReturn(testContactDto);

        ContactDto result = contactService.update(1L, testContactDto);

        assertThat(result).isNotNull();
        verify(contactMapper).updateEntity(testContactDto, testContact);
        verify(contactRepository).save(testContact);
    }

    @Test
    void update_WithChangedFreelanceAndClient_ShouldReassignRelationships() {
        Freelance otherFreelance = new Freelance();
        otherFreelance.setId(2L);
        Client otherClient = new Client();
        otherClient.setId(2L);

        testContactDto.setFreelanceId(2L);
        testContactDto.setClientId(2L);

        when(contactRepository.findById(1L)).thenReturn(Optional.of(testContact));
        when(freelanceRepository.findById(2L)).thenReturn(Optional.of(otherFreelance));
        when(clientRepository.findById(2L)).thenReturn(Optional.of(otherClient));
        when(contactRepository.save(testContact)).thenReturn(testContact);
        when(contactMapper.toDto(testContact)).thenReturn(testContactDto);

        contactService.update(1L, testContactDto);

        assertThat(testContact.getFreelance()).isEqualTo(otherFreelance);
        assertThat(testContact.getClient()).isEqualTo(otherClient);
    }

    @Test
    void update_WithNonExistingId_ShouldThrowResourceNotFoundException() {
        when(contactRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contactService.update(999L, testContactDto))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(contactRepository, never()).save(any());
    }

    @Test
    void delete_WithExistingId_ShouldDeleteContact() {
        when(contactRepository.existsById(1L)).thenReturn(true);

        contactService.delete(1L);

        verify(contactRepository).deleteById(1L);
    }

    @Test
    void delete_WithNonExistingId_ShouldThrowResourceNotFoundException() {
        when(contactRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> contactService.delete(999L))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(contactRepository, never()).deleteById(any());
    }

}
