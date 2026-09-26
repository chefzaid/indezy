package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.model.enums.SourceType;
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
 * Repository tests for SourceRepository
 * Tests JPA queries and database interactions
 */
@DataJpaTest
@ActiveProfiles("test")
class SourceRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SourceRepository sourceRepository;

    private Freelance testFreelance;
    private Source testSource;

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

        // Create test source
        testSource = new Source();
        testSource.setName("LinkedIn");
        testSource.setType(SourceType.JOB_BOARD);
        testSource.setLink("https://linkedin.com");
        testSource.setIsListing(false);
        testSource.setPopularityRating(4);
        testSource.setUsefulnessRating(5);
        testSource.setNotes("Professional networking platform");
        testSource.setFreelance(testFreelance);
        testSource = entityManager.persistAndFlush(testSource);
    }

    @Test
    void findByFreelanceId_WithExistingFreelanceId_ShouldReturnSources() {
        List<Source> sources = sourceRepository.findByFreelanceId(testFreelance.getId());

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getName()).isEqualTo("LinkedIn");
        assertThat(sources.get(0).getFreelance().getId()).isEqualTo(testFreelance.getId());
    }

    @Test
    void findByFreelanceId_WithNonExistentFreelanceId_ShouldReturnEmptyList() {
        List<Source> sources = sourceRepository.findByFreelanceId(999L);

        assertThat(sources).isEmpty();
    }

    @Test
    void save_ShouldPersistSource() {
        Source newSource = new Source();
        newSource.setName("New Source");
        newSource.setType(SourceType.CALL);
        newSource.setLink("https://newsource.com");
        newSource.setIsListing(true);
        newSource.setPopularityRating(3);
        newSource.setUsefulnessRating(4);
        newSource.setNotes("New source notes");
        newSource.setFreelance(testFreelance);

        Source savedSource = sourceRepository.save(newSource);

        assertThat(savedSource.getId()).isNotNull();
        assertThat(savedSource.getName()).isEqualTo("New Source");
        assertThat(savedSource.getCreatedAt()).isNotNull();
        assertThat(savedSource.getUpdatedAt()).isNotNull();
    }

    @Test
    void update_ShouldModifyExistingSource() {
        testSource.setName("Updated LinkedIn");
        testSource.setPopularityRating(5);

        Source updatedSource = sourceRepository.save(testSource);

        assertThat(updatedSource.getId()).isEqualTo(testSource.getId());
        assertThat(updatedSource.getName()).isEqualTo("Updated LinkedIn");
        assertThat(updatedSource.getPopularityRating()).isEqualTo(5);
        assertThat(updatedSource.getUpdatedAt()).isAfterOrEqualTo(updatedSource.getCreatedAt());
    }

    @Test
    void delete_ShouldRemoveSource() {
        Long sourceId = testSource.getId();

        sourceRepository.delete(testSource);
        entityManager.flush();

        Optional<Source> deletedSource = sourceRepository.findById(sourceId);
        assertThat(deletedSource).isEmpty();
    }
}
