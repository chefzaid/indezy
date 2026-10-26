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

    /** The tech stacks and daily rates of the opportunities added since {@code since} or won: what searches are suggested from. */
    @Query("select p.techStack, p.dailyRate from Project p where p.freelance.id = :freelanceId " +
        "and (p.createdAt >= :since or p.status = dev.swirlit.indezy.model.enums.ProjectStatus.WON)")
    List<Object[]> findRecentStacksAndRates(@Param("freelanceId") Long freelanceId, @Param("since") LocalDateTime since);

    List<Project> findByClientId(Long clientId);

    /** Archives a workspace's pipeline opportunities (contact, interviews, offer) last updated at or before {@code idleSince}. */
    @Modifying(clearAutomatically = true)
    @Query("update Project p set p.archivedAt = :now where p.freelance.id = :freelanceId and p.archivedAt is null " +
        "and p.status in (dev.swirlit.indezy.model.enums.ProjectStatus.CONTACT, dev.swirlit.indezy.model.enums.ProjectStatus.INTERVIEW, " +
        "dev.swirlit.indezy.model.enums.ProjectStatus.OFFER) and p.updatedAt <= :idleSince")
    int archiveIdle(@Param("freelanceId") Long freelanceId, @Param("idleSince") LocalDateTime idleSince, @Param("now") LocalDateTime now);

    List<Project> findByFreelanceIdAndSeasonId(Long freelanceId, Long seasonId);

    long countBySeasonId(Long seasonId);

    /** Opportunities of a workspace not yet attached to a season, created within the given window. */
    @Query("SELECT p FROM Project p WHERE p.freelance.id = :freelanceId AND p.season IS NULL " +
        "AND p.createdAt >= :from AND p.createdAt < :to")
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

    /** The workspace's first opportunity made from the posting at {@code link}, as the link field kept it. */
    @Query("SELECT p.id FROM Project p WHERE p.freelance.id = :freelanceId AND p.link = :link ORDER BY p.id")
    List<Long> findIdsByFreelanceIdAndLink(@Param("freelanceId") Long freelanceId, @Param("link") String link);
}
