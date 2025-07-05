package dev.byteworks.indezy.repository;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for FreelanceRepository
 * Tests JPA queries and database interactions
 */
@DataJpaTest
@ActiveProfiles("test")
class FreelanceRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FreelanceRepository freelanceRepository;

    private Freelance testFreelance;

    @BeforeEach
    void setUp() {
        testFreelance = new Freelance();
        testFreelance.setFirstName("John");
        testFreelance.setLastName("Doe");
        testFreelance.setEmail("john.doe@example.com");
        testFreelance.setPhone("123-456-7890");
        testFreelance.setEmploymentStatus(EmploymentStatus.FREELANCE);
        testFreelance.setDailyRate(500);
        testFreelance = entityManager.persistAndFlush(testFreelance);

        entityManager.clear();
    }

    @Test
    void findByEmail_WithExistingEmail_ShouldReturnFreelance() {
        Optional<Freelance> freelance = freelanceRepository.findByEmail("john.doe@example.com");

        assertThat(freelance).isPresent();
        assertThat(freelance.get().getFirstName()).isEqualTo("John");
        assertThat(freelance.get().getLastName()).isEqualTo("Doe");
        assertThat(freelance.get().getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    void findByEmail_WithNonExistentEmail_ShouldReturnEmpty() {
        Optional<Freelance> freelance = freelanceRepository.findByEmail("nonexistent@example.com");

        assertThat(freelance).isEmpty();
    }

    @Test
    void findByIdWithProjects_WithExistingId_ShouldReturnFreelanceWithProjects() {
        Optional<Freelance> freelance = freelanceRepository.findByIdWithProjects(testFreelance.getId());

        assertThat(freelance).isPresent();
        assertThat(freelance.get().getFirstName()).isEqualTo("John");
        assertThat(freelance.get().getProjects()).isNotNull();
    }

    @Test
    void findByIdWithProjects_WithNonExistentId_ShouldReturnEmpty() {
        Optional<Freelance> freelance = freelanceRepository.findByIdWithProjects(999L);

        assertThat(freelance).isEmpty();
    }

    @Test
    void save_WithValidFreelance_ShouldPersistFreelance() {
        Freelance newFreelance = new Freelance();
        newFreelance.setFirstName("Jane");
        newFreelance.setLastName("Smith");
        newFreelance.setEmail("jane.smith@example.com");
        newFreelance.setPhone("987-654-3210");
        newFreelance.setEmploymentStatus(EmploymentStatus.CDI);
        newFreelance.setDailyRate(600);

        Freelance savedFreelance = freelanceRepository.save(newFreelance);

        assertThat(savedFreelance.getId()).isNotNull();
        assertThat(savedFreelance.getFirstName()).isEqualTo("Jane");
        assertThat(savedFreelance.getLastName()).isEqualTo("Smith");
        assertThat(savedFreelance.getEmail()).isEqualTo("jane.smith@example.com");
        assertThat(savedFreelance.getEmploymentStatus()).isEqualTo(EmploymentStatus.CDI);
    }

    @Test
    void findById_WithExistingId_ShouldReturnFreelance() {
        Optional<Freelance> freelance = freelanceRepository.findById(testFreelance.getId());

        assertThat(freelance).isPresent();
        assertThat(freelance.get().getFirstName()).isEqualTo("John");
        assertThat(freelance.get().getLastName()).isEqualTo("Doe");
    }

    @Test
    void findById_WithNonExistentId_ShouldReturnEmpty() {
        Optional<Freelance> freelance = freelanceRepository.findById(999L);

        assertThat(freelance).isEmpty();
    }

    @Test
    void findAll_ShouldReturnAllFreelances() {
        List<Freelance> freelances = freelanceRepository.findAll();

        assertThat(freelances).hasSize(1);
        assertThat(freelances.get(0).getFirstName()).isEqualTo("John");
        assertThat(freelances.get(0).getLastName()).isEqualTo("Doe");
    }

    @Test
    void delete_WithExistingFreelance_ShouldRemoveFreelance() {
        Long freelanceId = testFreelance.getId();
        
        freelanceRepository.delete(testFreelance);
        entityManager.flush();

        Optional<Freelance> deletedFreelance = freelanceRepository.findById(freelanceId);
        assertThat(deletedFreelance).isEmpty();
    }

    @Test
    void existsByEmail_WithExistingEmail_ShouldReturnTrue() {
        boolean exists = freelanceRepository.existsByEmail("john.doe@example.com");

        assertThat(exists).isTrue();
    }

    @Test
    void existsByEmail_WithNonExistentEmail_ShouldReturnFalse() {
        boolean exists = freelanceRepository.existsByEmail("nonexistent@example.com");

        assertThat(exists).isFalse();
    }

    @Test
    void count_ShouldReturnCorrectCount() {
        long count = freelanceRepository.count();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void findByEmploymentStatus_WithExistingStatus_ShouldReturnFreelances() {
        // Create another freelance with different status
        Freelance cdiFreelance = new Freelance();
        cdiFreelance.setFirstName("Jane");
        cdiFreelance.setLastName("Smith");
        cdiFreelance.setEmail("jane.smith@example.com");
        cdiFreelance.setPhone("987-654-3210");
        cdiFreelance.setEmploymentStatus(EmploymentStatus.CDI);
        cdiFreelance.setDailyRate(600);
        entityManager.persistAndFlush(cdiFreelance);

        List<Freelance> freelanceStatus = freelanceRepository.findByEmploymentStatus(EmploymentStatus.FREELANCE);
        List<Freelance> cdiStatus = freelanceRepository.findByEmploymentStatus(EmploymentStatus.CDI);

        assertThat(freelanceStatus).hasSize(1);
        assertThat(freelanceStatus.get(0).getEmploymentStatus()).isEqualTo(EmploymentStatus.FREELANCE);
        
        assertThat(cdiStatus).hasSize(1);
        assertThat(cdiStatus.get(0).getEmploymentStatus()).isEqualTo(EmploymentStatus.CDI);
    }

    @Test
    void findByFirstNameContainingIgnoreCase_WithMatchingName_ShouldReturnFreelances() {
        List<Freelance> freelances = freelanceRepository.findByFirstNameContainingIgnoreCase("john");

        assertThat(freelances).hasSize(1);
        assertThat(freelances.get(0).getFirstName()).isEqualToIgnoringCase("John");
    }

    @Test
    void findByFirstNameContainingIgnoreCase_WithNonMatchingName_ShouldReturnEmpty() {
        List<Freelance> freelances = freelanceRepository.findByFirstNameContainingIgnoreCase("nonexistent");

        assertThat(freelances).isEmpty();
    }

    @Test
    void findByLastNameContainingIgnoreCase_WithMatchingName_ShouldReturnFreelances() {
        List<Freelance> freelances = freelanceRepository.findByLastNameContainingIgnoreCase("doe");

        assertThat(freelances).hasSize(1);
        assertThat(freelances.get(0).getLastName()).isEqualToIgnoringCase("Doe");
    }

    @Test
    void findByDailyRateBetween_WithValidRange_ShouldReturnFreelances() {
        List<Freelance> freelances = freelanceRepository.findByDailyRateBetween(400, 600);

        assertThat(freelances).hasSize(1);
        assertThat(freelances.get(0).getDailyRate()).isBetween(400, 600);
    }

    @Test
    void findByDailyRateBetween_WithInvalidRange_ShouldReturnEmpty() {
        List<Freelance> freelances = freelanceRepository.findByDailyRateBetween(700, 800);

        assertThat(freelances).isEmpty();
    }
}
