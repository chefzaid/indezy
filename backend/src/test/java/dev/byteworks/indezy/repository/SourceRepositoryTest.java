package dev.byteworks.indezy.repository;

import dev.byteworks.indezy.model.Freelance;
import dev.byteworks.indezy.model.Source;
import dev.byteworks.indezy.model.enums.EmploymentStatus;
import dev.byteworks.indezy.model.enums.SourceType;
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
    void findByFreelanceIdAndType_WithMatchingType_ShouldReturnFilteredSources() {
        // Create another source with different type
        Source socialMediaSource = new Source();
        socialMediaSource.setName("Twitter");
        socialMediaSource.setType(SourceType.SOCIAL_MEDIA);
        socialMediaSource.setLink("https://twitter.com");
        socialMediaSource.setIsListing(false);
        socialMediaSource.setPopularityRating(5);
        socialMediaSource.setUsefulnessRating(4);
        socialMediaSource.setFreelance(testFreelance);
        entityManager.persistAndFlush(socialMediaSource);

        List<Source> jobBoardSources = sourceRepository.findByFreelanceIdAndType(
                testFreelance.getId(), SourceType.JOB_BOARD);
        List<Source> socialMediaSources = sourceRepository.findByFreelanceIdAndType(
                testFreelance.getId(), SourceType.SOCIAL_MEDIA);

        assertThat(jobBoardSources).hasSize(1);
        assertThat(jobBoardSources.get(0).getName()).isEqualTo("LinkedIn");
        assertThat(jobBoardSources.get(0).getType()).isEqualTo(SourceType.JOB_BOARD);

        assertThat(socialMediaSources).hasSize(1);
        assertThat(socialMediaSources.get(0).getName()).isEqualTo("Twitter");
        assertThat(socialMediaSources.get(0).getType()).isEqualTo(SourceType.SOCIAL_MEDIA);
    }

    @Test
    void findByFreelanceIdAndIsListing_WithListingSources_ShouldReturnFilteredSources() {
        // Create another source that is a listing
        Source listingSource = new Source();
        listingSource.setName("Indeed");
        listingSource.setType(SourceType.JOB_BOARD);
        listingSource.setLink("https://indeed.com");
        listingSource.setIsListing(true);
        listingSource.setPopularityRating(3);
        listingSource.setUsefulnessRating(3);
        listingSource.setFreelance(testFreelance);
        entityManager.persistAndFlush(listingSource);

        List<Source> listingSources = sourceRepository.findByFreelanceIdAndIsListing(
                testFreelance.getId(), true);
        List<Source> nonListingSources = sourceRepository.findByFreelanceIdAndIsListing(
                testFreelance.getId(), false);

        assertThat(listingSources).hasSize(1);
        assertThat(listingSources.get(0).getName()).isEqualTo("Indeed");
        assertThat(listingSources.get(0).getIsListing()).isTrue();

        assertThat(nonListingSources).hasSize(1);
        assertThat(nonListingSources.get(0).getName()).isEqualTo("LinkedIn");
        assertThat(nonListingSources.get(0).getIsListing()).isFalse();
    }

    @Test
    void findByFreelanceIdAndPopularityRatingGreaterThanEqual_ShouldReturnFilteredSources() {
        // Create another source with lower rating
        Source lowRatingSource = new Source();
        lowRatingSource.setName("LowRating Source");
        lowRatingSource.setType(SourceType.JOB_BOARD);
        lowRatingSource.setPopularityRating(2);
        lowRatingSource.setUsefulnessRating(2);
        lowRatingSource.setFreelance(testFreelance);
        entityManager.persistAndFlush(lowRatingSource);

        List<Source> highRatingSources = sourceRepository.findByFreelanceIdAndPopularityRatingGreaterThanEqual(
                testFreelance.getId(), 4);

        assertThat(highRatingSources).hasSize(1);
        assertThat(highRatingSources.get(0).getName()).isEqualTo("LinkedIn");
        assertThat(highRatingSources.get(0).getPopularityRating()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void findByFreelanceIdAndUsefulnessRatingGreaterThanEqual_ShouldReturnFilteredSources() {
        List<Source> highUsefulnessSources = sourceRepository.findByFreelanceIdAndUsefulnessRatingGreaterThanEqual(
                testFreelance.getId(), 5);

        assertThat(highUsefulnessSources).hasSize(1);
        assertThat(highUsefulnessSources.get(0).getName()).isEqualTo("LinkedIn");
        assertThat(highUsefulnessSources.get(0).getUsefulnessRating()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void findByIdWithProjects_WithExistingId_ShouldReturnSourceWithProjects() {
        Optional<Source> source = sourceRepository.findByIdWithProjects(testSource.getId());

        assertThat(source).isPresent();
        assertThat(source.get().getName()).isEqualTo("LinkedIn");
        assertThat(source.get().getProjects()).isNotNull();
    }

    @Test
    void findByIdWithProjects_WithNonExistentId_ShouldReturnEmpty() {
        Optional<Source> source = sourceRepository.findByIdWithProjects(999L);

        assertThat(source).isEmpty();
    }

    @Test
    void findAveragePopularityRatingByFreelanceId_ShouldReturnAverageRating() {
        // Create another source to test average calculation
        Source anotherSource = new Source();
        anotherSource.setName("Another Source");
        anotherSource.setType(SourceType.EMAIL);
        anotherSource.setPopularityRating(2); // Average with 4 should be 3.0
        anotherSource.setUsefulnessRating(3);
        anotherSource.setFreelance(testFreelance);
        entityManager.persistAndFlush(anotherSource);

        Double averageRating = sourceRepository.findAveragePopularityRatingByFreelanceId(testFreelance.getId());

        assertThat(averageRating).isEqualTo(3.0);
    }

    @Test
    void findAverageUsefulnessRatingByFreelanceId_ShouldReturnAverageRating() {
        // Create another source to test average calculation
        Source anotherSource = new Source();
        anotherSource.setName("Another Source");
        anotherSource.setType(SourceType.EMAIL);
        anotherSource.setPopularityRating(3);
        anotherSource.setUsefulnessRating(3); // Average with 5 should be 4.0
        anotherSource.setFreelance(testFreelance);
        entityManager.persistAndFlush(anotherSource);

        Double averageRating = sourceRepository.findAverageUsefulnessRatingByFreelanceId(testFreelance.getId());

        assertThat(averageRating).isEqualTo(4.0);
    }

    @Test
    void findAveragePopularityRatingByFreelanceId_WithNoSources_ShouldReturnNull() {
        Double averageRating = sourceRepository.findAveragePopularityRatingByFreelanceId(999L);

        assertThat(averageRating).isNull();
    }

    @Test
    void findDistinctTypesByFreelanceId_ShouldReturnUniqueTypes() {
        // Create sources with different types
        Source socialMediaSource = new Source();
        socialMediaSource.setName("Twitter");
        socialMediaSource.setType(SourceType.SOCIAL_MEDIA);
        socialMediaSource.setFreelance(testFreelance);
        entityManager.persistAndFlush(socialMediaSource);

        Source emailSource = new Source();
        emailSource.setName("Email Campaign");
        emailSource.setType(SourceType.EMAIL);
        emailSource.setFreelance(testFreelance);
        entityManager.persistAndFlush(emailSource);

        List<SourceType> types = sourceRepository.findDistinctTypesByFreelanceId(testFreelance.getId());

        assertThat(types).hasSize(3);
        assertThat(types).contains(SourceType.JOB_BOARD, SourceType.SOCIAL_MEDIA, SourceType.EMAIL);
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
    void update_ShouldModifyExistingSource() throws InterruptedException {
        // Add a small delay to ensure updatedAt is different
        Thread.sleep(1);

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
