package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Repository tests for ClientRepository
 * Tests JPA queries and database interactions
 */
@DataJpaTest
@ActiveProfiles("test")
class ClientRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ClientRepository clientRepository;

    private Freelance testFreelance;
    private Client testClient;

    @BeforeEach
    void setUp() {
        // Create test freelance
        testFreelance = new Freelance();
        testFreelance.setFirstName("John");
        testFreelance.setLastName("Doe");
        testFreelance.setEmail("john.doe@example.com");
        testFreelance.setPhone("123-456-7890");
        testFreelance.setStatus(EmploymentStatus.FREELANCE);
        testFreelance = entityManager.persistAndFlush(testFreelance);

        // Create test client
        testClient = new Client();
        testClient.setCompanyName("Test Company");
        testClient.setAddress("123 Test St");
        testClient.setCity("Test City");
        testClient.setDomain("Technology");
        testClient.setIsFinal(true);
        testClient.setNotes("Test notes");
        testClient.setFreelance(testFreelance);
        testClient = entityManager.persistAndFlush(testClient);
    }

    @Test
    void findByFreelanceId_WithExistingFreelanceId_ShouldReturnClients() {
        List<Client> clients = clientRepository.findByFreelanceId(testFreelance.getId());

        assertThat(clients).hasSize(1);
        assertThat(clients.get(0).getCompanyName()).isEqualTo("Test Company");
        assertThat(clients.get(0).getFreelance().getId()).isEqualTo(testFreelance.getId());
    }

    @Test
    void findByFreelanceId_WithNonExistentFreelanceId_ShouldReturnEmptyList() {
        List<Client> clients = clientRepository.findByFreelanceId(999L);

        assertThat(clients).isEmpty();
    }

    @Test
    void save_ShouldPersistClient() {
        Client newClient = new Client();
        newClient.setCompanyName("New Company");
        newClient.setAddress("456 New St");
        newClient.setCity("New City");
        newClient.setDomain("Healthcare");
        newClient.setIsFinal(false);
        newClient.setNotes("New client notes");
        newClient.setFreelance(testFreelance);

        Client savedClient = clientRepository.save(newClient);

        assertThat(savedClient.getId()).isNotNull();
        assertThat(savedClient.getCompanyName()).isEqualTo("New Company");
        assertThat(savedClient.getCreatedAt()).isNotNull();
        assertThat(savedClient.getUpdatedAt()).isNotNull();
    }

    @Test
    void update_ShouldModifyExistingClient() {
        testClient.setCompanyName("Updated Company");
        testClient.setDomain("Updated Domain");

        Client updatedClient = clientRepository.save(testClient);

        assertThat(updatedClient.getId()).isEqualTo(testClient.getId());
        assertThat(updatedClient.getCompanyName()).isEqualTo("Updated Company");
        assertThat(updatedClient.getDomain()).isEqualTo("Updated Domain");
        assertThat(updatedClient.getUpdatedAt()).isAfterOrEqualTo(updatedClient.getCreatedAt());
    }

    @Test
    void delete_ShouldRemoveClient() {
        Long clientId = testClient.getId();

        clientRepository.delete(testClient);
        entityManager.flush();

        Optional<Client> deletedClient = clientRepository.findById(clientId);
        assertThat(deletedClient).isEmpty();
    }

    @Test
    void save_ShouldPersistRatingAndBlacklist() {
        Client blacklisted = new Client();
        blacklisted.setCompanyName("Bad Payer Inc");
        blacklisted.setCity("Paris");
        blacklisted.setIsFinal(true);
        blacklisted.setRating(2);
        blacklisted.setIsBlacklisted(true);
        blacklisted.setBlacklistReason("Repeated payment delays");
        blacklisted.setFreelance(testFreelance);

        Client saved = clientRepository.saveAndFlush(blacklisted);
        entityManager.clear();

        Client reloaded = clientRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getRating()).isEqualTo(2);
        assertThat(reloaded.getIsBlacklisted()).isTrue();
        assertThat(reloaded.getBlacklistReason()).isEqualTo("Repeated payment delays");
    }

    @Test
    void setIsBlacklisted_ShouldCoerceNullToFalse() {
        // The non-nullable column must always be populated, even when callers omit the flag.
        testClient.setIsBlacklisted(null);

        Client saved = clientRepository.saveAndFlush(testClient);

        assertThat(saved.getIsBlacklisted()).isFalse();
    }
}
