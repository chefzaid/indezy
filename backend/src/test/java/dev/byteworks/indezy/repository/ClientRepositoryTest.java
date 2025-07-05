package dev.byteworks.indezy.repository;

import dev.byteworks.indezy.model.Client;
import dev.byteworks.indezy.model.Freelance;
import dev.byteworks.indezy.model.enums.EmploymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
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
    void findByFreelanceIdAndIsFinal_WithFinalClients_ShouldReturnFilteredClients() {
        // Create another client that is not final
        Client nonFinalClient = new Client();
        nonFinalClient.setCompanyName("Non-Final Company");
        nonFinalClient.setAddress("456 Test Ave");
        nonFinalClient.setCity("Another City");
        nonFinalClient.setIsFinal(false);
        nonFinalClient.setFreelance(testFreelance);
        entityManager.persistAndFlush(nonFinalClient);

        List<Client> finalClients = clientRepository.findByFreelanceIdAndIsFinal(testFreelance.getId(), true);
        List<Client> nonFinalClients = clientRepository.findByFreelanceIdAndIsFinal(testFreelance.getId(), false);

        assertThat(finalClients).hasSize(1);
        assertThat(finalClients.get(0).getCompanyName()).isEqualTo("Test Company");
        assertThat(finalClients.get(0).getIsFinal()).isTrue();

        assertThat(nonFinalClients).hasSize(1);
        assertThat(nonFinalClients.get(0).getCompanyName()).isEqualTo("Non-Final Company");
        assertThat(nonFinalClients.get(0).getIsFinal()).isFalse();
    }

    @Test
    void findByFreelanceIdAndCompanyNameContaining_WithMatchingName_ShouldReturnClients() {
        List<Client> clients = clientRepository.findByFreelanceIdAndCompanyNameContaining(
                testFreelance.getId(), "Test");

        assertThat(clients).hasSize(1);
        assertThat(clients.get(0).getCompanyName()).contains("Test");
    }

    @Test
    void findByFreelanceIdAndCompanyNameContaining_WithNonMatchingName_ShouldReturnEmptyList() {
        List<Client> clients = clientRepository.findByFreelanceIdAndCompanyNameContaining(
                testFreelance.getId(), "NonExistent");

        assertThat(clients).isEmpty();
    }

    @Test
    void findByFreelanceIdAndCity_WithMatchingCity_ShouldReturnClients() {
        List<Client> clients = clientRepository.findByFreelanceIdAndCity(
                testFreelance.getId(), "Test City");

        assertThat(clients).hasSize(1);
        assertThat(clients.get(0).getCity()).isEqualTo("Test City");
    }

    @Test
    void findByFreelanceIdAndCity_WithNonMatchingCity_ShouldReturnEmptyList() {
        List<Client> clients = clientRepository.findByFreelanceIdAndCity(
                testFreelance.getId(), "Non-Existent City");

        assertThat(clients).isEmpty();
    }

    @Test
    void findByIdWithProjects_WithExistingId_ShouldReturnClientWithProjects() {
        Optional<Client> client = clientRepository.findByIdWithProjects(testClient.getId());

        assertThat(client).isPresent();
        assertThat(client.get().getCompanyName()).isEqualTo("Test Company");
        assertThat(client.get().getProjects()).isNotNull();
    }

    @Test
    void findByIdWithProjects_WithNonExistentId_ShouldReturnEmpty() {
        Optional<Client> client = clientRepository.findByIdWithProjects(999L);

        assertThat(client).isEmpty();
    }

    @Test
    void findByIdWithContacts_WithExistingId_ShouldReturnClientWithContacts() {
        Optional<Client> client = clientRepository.findByIdWithContacts(testClient.getId());

        assertThat(client).isPresent();
        assertThat(client.get().getCompanyName()).isEqualTo("Test Company");
        assertThat(client.get().getContacts()).isNotNull();
    }

    @Test
    void findDistinctCitiesByFreelanceId_ShouldReturnUniqueCities() {
        // Create another client in a different city
        Client anotherClient = new Client();
        anotherClient.setCompanyName("Another Company");
        anotherClient.setAddress("789 Another St");
        anotherClient.setCity("Another City");
        anotherClient.setIsFinal(true);
        anotherClient.setFreelance(testFreelance);
        entityManager.persistAndFlush(anotherClient);

        List<String> cities = clientRepository.findDistinctCitiesByFreelanceId(testFreelance.getId());

        assertThat(cities).hasSize(2);
        assertThat(cities).contains("Test City", "Another City");
    }

    @Test
    void findDistinctDomainsByFreelanceId_ShouldReturnUniqueDomains() {
        // Create another client in a different domain
        Client anotherClient = new Client();
        anotherClient.setCompanyName("Finance Company");
        anotherClient.setAddress("789 Finance St");
        anotherClient.setCity("Finance City");
        anotherClient.setDomain("Finance");
        anotherClient.setIsFinal(true);
        anotherClient.setFreelance(testFreelance);
        entityManager.persistAndFlush(anotherClient);

        List<String> domains = clientRepository.findDistinctDomainsByFreelanceId(testFreelance.getId());

        assertThat(domains).hasSize(2);
        assertThat(domains).contains("Technology", "Finance");
    }

    @Test
    void findDistinctDomainsByFreelanceId_WithNullDomains_ShouldExcludeNulls() {
        // Create another client with null domain
        Client nullDomainClient = new Client();
        nullDomainClient.setCompanyName("No Domain Company");
        nullDomainClient.setAddress("999 No Domain St");
        nullDomainClient.setCity("No Domain City");
        nullDomainClient.setDomain(null);
        nullDomainClient.setIsFinal(true);
        nullDomainClient.setFreelance(testFreelance);
        entityManager.persistAndFlush(nullDomainClient);

        List<String> domains = clientRepository.findDistinctDomainsByFreelanceId(testFreelance.getId());

        assertThat(domains).hasSize(1);
        assertThat(domains).contains("Technology");
        assertThat(domains).doesNotContainNull();
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
    void update_ShouldModifyExistingClient() throws InterruptedException {
        // Add a small delay to ensure updatedAt is different
        Thread.sleep(1);

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
}
