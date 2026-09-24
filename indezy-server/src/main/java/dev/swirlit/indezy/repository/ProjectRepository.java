package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.WorkMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByFreelanceId(Long freelanceId);

    List<Project> findByClientId(Long clientId);

    List<Project> findByFreelanceIdAndSeasonId(Long freelanceId, Long seasonId);

    long countBySeasonId(Long seasonId);

    /** Opportunities of a workspace not yet attached to a season, created within the given window. */
    @Query("SELECT p FROM Project p WHERE p.freelance.id = :freelanceId AND p.season IS NULL "
        + "AND p.createdAt >= :from AND p.createdAt < :to")
    List<Project> findUnassignedCreatedBetween(@Param("freelanceId") Long freelanceId,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    /** Detaches every opportunity from a season before the season is deleted. */
    @Modifying
    @Query("UPDATE Project p SET p.season = NULL WHERE p.season.id = :seasonId")
    int clearSeason(@Param("seasonId") Long seasonId);

    List<Project> findBySourceId(Long sourceId);

    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.steps WHERE p.id = :id")
    Optional<Project> findByIdWithSteps(@Param("id") Long id);

    @Query("SELECT p FROM Project p WHERE p.freelance.id = :freelanceId AND p.dailyRate >= :minRate")
    List<Project> findByFreelanceIdAndDailyRateGreaterThanEqual(@Param("freelanceId") Long freelanceId, @Param("minRate") Integer minRate);

    @Query("SELECT p FROM Project p WHERE p.freelance.id = :freelanceId AND p.dailyRate <= :maxRate")
    List<Project> findByFreelanceIdAndDailyRateLessThanEqual(@Param("freelanceId") Long freelanceId, @Param("maxRate") Integer maxRate);

    @Query("SELECT p FROM Project p WHERE p.freelance.id = :freelanceId AND p.workMode = :workMode")
    List<Project> findByFreelanceIdAndWorkMode(@Param("freelanceId") Long freelanceId, @Param("workMode") WorkMode workMode);

    @Query("SELECT p FROM Project p WHERE p.freelance.id = :freelanceId AND p.startDate >= :startDate")
    List<Project> findByFreelanceIdAndStartDateAfter(@Param("freelanceId") Long freelanceId, @Param("startDate") LocalDate startDate);

    @Query("SELECT p FROM Project p WHERE p.freelance.id = :freelanceId AND p.techStack LIKE %:techStack%")
    List<Project> findByFreelanceIdAndTechStackContaining(@Param("freelanceId") Long freelanceId, @Param("techStack") String techStack);

    @Query("SELECT AVG(p.dailyRate) FROM Project p WHERE p.freelance.id = :freelanceId")
    Double findAverageDailyRateByFreelanceId(@Param("freelanceId") Long freelanceId);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.freelance.id = :freelanceId")
    Long countByFreelanceId(@Param("freelanceId") Long freelanceId);

    @Query("SELECT p.status, COUNT(p) FROM Project p WHERE p.freelance.id = :freelanceId GROUP BY p.status")
    List<Object[]> countByFreelanceIdGroupByStatus(@Param("freelanceId") Long freelanceId);

    @Query("SELECT p.workMode, COUNT(p) FROM Project p WHERE p.freelance.id = :freelanceId AND p.workMode IS NOT NULL GROUP BY p.workMode")
    List<Object[]> countByFreelanceIdGroupByWorkMode(@Param("freelanceId") Long freelanceId);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.freelance.id = :freelanceId AND p.status = 'WON'")
    Long countWonByFreelanceId(@Param("freelanceId") Long freelanceId);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.freelance.id = :freelanceId AND p.status = 'LOST'")
    Long countLostByFreelanceId(@Param("freelanceId") Long freelanceId);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.freelance.id = :freelanceId AND p.status NOT IN ('LOST', 'WON')")
    Long countActiveByFreelanceId(@Param("freelanceId") Long freelanceId);

    /** Owner workspace of the project, used by access checks without loading the entity graph. */
    @Query("SELECT p.freelance.id FROM Project p WHERE p.id = :id")
    Optional<Long> findOwnerFreelanceIdById(@Param("id") Long id);
}
