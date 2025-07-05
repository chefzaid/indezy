package dev.byteworks.indezy.repository;

import dev.byteworks.indezy.model.Source;
import dev.byteworks.indezy.model.enums.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SourceRepository extends JpaRepository<Source, Long> {

    List<Source> findByFreelanceId(Long freelanceId);

    List<Source> findByFreelanceIdAndType(Long freelanceId, SourceType type);

    List<Source> findByFreelanceIdAndIsListing(Long freelanceId, Boolean isListing);

    @Query("SELECT s FROM Source s WHERE s.freelance.id = :freelanceId AND s.name LIKE %:name%")
    List<Source> findByFreelanceIdAndNameContaining(@Param("freelanceId") Long freelanceId, @Param("name") String name);

    @Query("SELECT s FROM Source s LEFT JOIN FETCH s.projects WHERE s.id = :id")
    Optional<Source> findByIdWithProjects(@Param("id") Long id);

    @Query("SELECT s FROM Source s WHERE s.freelance.id = :freelanceId AND s.popularityRating >= :minRating")
    List<Source> findByFreelanceIdAndPopularityRatingGreaterThanEqual(@Param("freelanceId") Long freelanceId, @Param("minRating") Integer minRating);

    @Query("SELECT s FROM Source s WHERE s.freelance.id = :freelanceId AND s.usefulnessRating >= :minRating")
    List<Source> findByFreelanceIdAndUsefulnessRatingGreaterThanEqual(@Param("freelanceId") Long freelanceId, @Param("minRating") Integer minRating);

    @Query("SELECT AVG(s.popularityRating) FROM Source s WHERE s.freelance.id = :freelanceId AND s.popularityRating IS NOT NULL")
    Double findAveragePopularityRatingByFreelanceId(@Param("freelanceId") Long freelanceId);

    @Query("SELECT AVG(s.usefulnessRating) FROM Source s WHERE s.freelance.id = :freelanceId AND s.usefulnessRating IS NOT NULL")
    Double findAverageUsefulnessRatingByFreelanceId(@Param("freelanceId") Long freelanceId);
}
