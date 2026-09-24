package dev.swirlit.indezy.repository;

import java.util.Optional;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.enums.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InterviewStepRepository extends JpaRepository<InterviewStep, Long> {

    List<InterviewStep> findByProjectId(Long projectId);

    List<InterviewStep> findByProjectIdAndStatus(Long projectId, StepStatus status);

    @Query("SELECT s FROM InterviewStep s WHERE s.project.id = :projectId ORDER BY s.date ASC")
    List<InterviewStep> findByProjectIdOrderByDate(@Param("projectId") Long projectId);

    @Query("SELECT s FROM InterviewStep s WHERE s.project.freelance.id = :freelanceId AND s.date BETWEEN :startDate AND :endDate")
    List<InterviewStep> findByFreelanceIdAndDateBetween(@Param("freelanceId") Long freelanceId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT s FROM InterviewStep s WHERE s.project.freelance.id = :freelanceId AND (:status IS NULL OR s.status = :status)")
    List<InterviewStep> findByFreelanceIdAndStatus(@Param("freelanceId") Long freelanceId, @Param("status") StepStatus status);

    @Query("SELECT COUNT(s) FROM InterviewStep s WHERE s.project.id = :projectId AND s.status = :status")
    Long countByProjectIdAndStatus(@Param("projectId") Long projectId, @Param("status") StepStatus status);

    /** Owner workspace of the interviewstep, used by access checks without loading the entity graph. */
    @Query("SELECT s.project.freelance.id FROM InterviewStep s WHERE s.id = :id")
    Optional<Long> findOwnerFreelanceIdById(@Param("id") Long id);
}
