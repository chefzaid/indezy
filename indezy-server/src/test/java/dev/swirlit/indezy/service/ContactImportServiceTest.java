package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ContactImportResultDto;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContactImportServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ContactRepository contactRepository;

    @InjectMocks
    private ContactImportService contactImportService;

    private Client client;

    @BeforeEach
    void setUp() {
        Freelance freelance = new Freelance();
        freelance.setId(1L);
        client = new Client();
        client.setId(5L);
        client.setFreelance(freelance);
    }

    @Test
    void parseReadsCsvColumnsIncludingQuotedCommas() {
        String csv = """
            First Name,Last Name,Email,Phone
            Marie,Dubois,marie@x.com,+33 1 23
            "Anne","Smith, Jr.",anne@y.com,
            """;

        List<ContactImportService.ParsedContact> parsed = contactImportService.parse(csv);

        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0)).extracting(
            ContactImportService.ParsedContact::firstName,
            ContactImportService.ParsedContact::lastName,
            ContactImportService.ParsedContact::email,
            ContactImportService.ParsedContact::phone)
            .containsExactly("Marie", "Dubois", "marie@x.com", "+33 1 23");
        assertThat(parsed.get(1).lastName()).isEqualTo("Smith, Jr.");
    }

    @Test
    void parseReadsVcardEntries() {
        String vcard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:John Doe
            EMAIL;TYPE=WORK:john@doe.com
            TEL;TYPE=CELL:+33 6 12 34
            END:VCARD
            """;

        List<ContactImportService.ParsedContact> parsed = contactImportService.parse(vcard);

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().firstName()).isEqualTo("John");
        assertThat(parsed.getFirst().lastName()).isEqualTo("Doe");
        assertThat(parsed.getFirst().email()).isEqualTo("john@doe.com");
        assertThat(parsed.getFirst().phone()).isEqualTo("+33 6 12 34");
    }

    @Test
    void importForClientSkipsDuplicateEmailsAndCreatesTheRest() {
        String csv = """
            First Name,Last Name,Email
            Marie,Dubois,marie@x.com
            Pierre,Martin,pierre@y.com
            """;
        when(clientRepository.findById(5L)).thenReturn(Optional.of(client));
        when(contactRepository.existsByEmailAndFreelanceId("marie@x.com", 1L)).thenReturn(true);
        when(contactRepository.existsByEmailAndFreelanceId("pierre@y.com", 1L)).thenReturn(false);

        ContactImportResultDto result = contactImportService.importForClient(5L, csv);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(1);

        verify(contactRepository, times(1)).save(any(Contact.class));
    }
}
