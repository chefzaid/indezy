package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Detaches every opportunity from a source before the source is deleted. */
    @Modifying
    @Query("UPDATE Project p SET p.source = NULL WHERE p.source.id = :sourceId")
    int clearSource(@Param("sourceId") Long sourceId);

    /** Detaches every opportunity from a season before the season is deleted. */
    @Modifying
    @Query("UPDATE Project p SET p.season = NULL WHERE p.season.id = :seasonId")
    int clearSeason(@Param("seasonId") Long seasonId);

    /** Owner workspace of the project, used by access checks without loading the entity graph. */
    @Query("SELECT p.freelance.id FROM Project p WHERE p.id = :id")
    Optional<Long> findOwnerFreelanceIdById(@Param("id") Long id);
}
