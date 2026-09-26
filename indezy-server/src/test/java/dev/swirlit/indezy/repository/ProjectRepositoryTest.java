package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.*;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.model.enums.SourceType;
import dev.swirlit.indezy.model.enums.WorkMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for ProjectRepository
 * Tests JPA queries and database interactions
 */
@DataJpaTest
@ActiveProfiles("test")
class ProjectRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProjectRepository projectRepository;

    private Freelance testFreelance;
    private Client testClient;
    private Source testSource;
    private Project testProject;

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
        testClient.setCompanyName("Test Client");
        testClient.setAddress("123 Test St");
        testClient.setCity("Test City");
        testClient.setIsFinal(true);
        testClient.setFreelance(testFreelance);
        testClient = entityManager.persistAndFlush(testClient);

        // Create test source
        testSource = new Source();
        testSource.setName("Test Source");
        testSource.setType(SourceType.JOB_BOARD);
        testSource.setFreelance(testFreelance);
        testSource = entityManager.persistAndFlush(testSource);

        // Create test project
        testProject = new Project();
        testProject.setRole("Full Stack Developer");
        testProject.setDescription("Test project description");
        testProject.setTechStack("Java, Spring Boot, Angular");
        testProject.setDailyRate(600);
        testProject.setWorkMode(WorkMode.REMOTE);
        testProject.setStartDate(LocalDate.now());
        testProject.setDurationInMonths(6);
        testProject.setFreelance(testFreelance);
        testProject.setClient(testClient);
        testProject.setSource(testSource);
        testProject = entityManager.persistAndFlush(testProject);

        entityManager.clear();
    }

    @Test
    void findByFreelanceId_WithExistingFreelanceId_ShouldReturnProjects() {
        List<Project> projects = projectRepository.findByFreelanceId(testFreelance.getId());

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).getRole()).isEqualTo("Full Stack Developer");
        assertThat(projects.get(0).getFreelance().getId()).isEqualTo(testFreelance.getId());
    }

    @Test
    void findByFreelanceId_WithNonExistentFreelanceId_ShouldReturnEmptyList() {
        List<Project> projects = projectRepository.findByFreelanceId(999L);

        assertThat(projects).isEmpty();
    }

    @Test
    void save_WithValidProject_ShouldPersistProject() {
        Project newProject = new Project();
        newProject.setRole("Backend Developer");
        newProject.setDescription("New test project");
        newProject.setTechStack("Java, Spring Boot");
        newProject.setDailyRate(550);
        newProject.setWorkMode(WorkMode.HYBRID);
        newProject.setStartDate(LocalDate.now());
        newProject.setDurationInMonths(3);
        newProject.setFreelance(testFreelance);
        newProject.setClient(testClient);
        newProject.setSource(testSource);

        Project savedProject = projectRepository.save(newProject);

        assertThat(savedProject.getId()).isNotNull();
        assertThat(savedProject.getRole()).isEqualTo("Backend Developer");
        assertThat(savedProject.getDailyRate()).isEqualTo(550);
    }

    @Test
    void delete_WithExistingProject_ShouldRemoveProject() {
        Long projectId = testProject.getId();
        
        projectRepository.delete(testProject);
        entityManager.flush();

        Optional<Project> deletedProject = projectRepository.findById(projectId);
        assertThat(deletedProject).isEmpty();
    }

    @Test
    void findAll_ShouldReturnAllProjects() {
        List<Project> projects = projectRepository.findAll();

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).getRole()).isEqualTo("Full Stack Developer");
    }
}
