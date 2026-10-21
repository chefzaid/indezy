package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Freelance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FreelanceRepository extends JpaRepository<Freelance, Long> {

    Optional<Freelance> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Account and workspace ownership use the same email link as CurrentWorkspaceResolver. */
    @Query("SELECT u.inactivityDays FROM User u, Freelance f WHERE f.id = :freelanceId AND u.email = f.email")
    Optional<Integer> findInactivityDays(@Param("freelanceId") Long freelanceId);

    @Query("SELECT u.targetDailyRate FROM User u, Freelance f WHERE f.id = :freelanceId AND u.email = f.email")
    Optional<Integer> findTargetDailyRate(@Param("freelanceId") Long freelanceId);

    /** The city of the workspace owner's profile, where offer distances are measured from. */
    @Query("SELECT u.city FROM User u, Freelance f WHERE f.id = :freelanceId AND u.email = f.email")
    Optional<String> findProfileCity(@Param("freelanceId") Long freelanceId);

    /** The job title of the workspace owner's profile, which offer searches are suggested from. */
    @Query("SELECT u.position FROM User u, Freelance f WHERE f.id = :freelanceId AND u.email = f.email")
    Optional<String> findProfilePosition(@Param("freelanceId") Long freelanceId);

    /** When the workspace is next available, which offers' start dates are matched against. */
    @Query("SELECT f.availabilityDate FROM Freelance f WHERE f.id = :freelanceId")
    Optional<LocalDate> findAvailabilityDate(@Param("freelanceId") Long freelanceId);

    /** The skills of the workspace owner's profile, which offers are matched against. */
    @Query("SELECT s FROM User u JOIN u.skills s, Freelance f WHERE f.id = :freelanceId AND u.email = f.email")
    List<String> findProfileSkills(@Param("freelanceId") Long freelanceId);


    /** Claims the workspace's job board sources; 1 for the caller that gives them, 0 when it already has them. */
    @Modifying
    @Query("update Freelance f set f.jobBoardSourcesSeeded = true where f.id = :freelanceId " +
        "and (f.jobBoardSourcesSeeded is null or f.jobBoardSourcesSeeded = false)")
    int claimJobBoardSources(@Param("freelanceId") Long freelanceId);

    @Query("select f.id from Freelance f where f.jobBoardSourcesSeeded is null or f.jobBoardSourcesSeeded = false order by f.id")
    List<Long> findIdsWithoutJobBoardSources();
}
