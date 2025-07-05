package dev.byteworks.indezy.repository;

import dev.byteworks.indezy.model.InterviewStep;
import dev.byteworks.indezy.model.enums.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InterviewStepRepository extends JpaRepository<InterviewStep, Long> {

    List<InterviewStep> findByProjectId(Long projectId);

    List<InterviewStep> findByProjectIdAndStatus(Long projectId, StepStatus status);

    @Query("SELECT s FROM InterviewStep s WHERE s.project.id = :projectId ORDER BY s.date ASC")
    List<InterviewStep> findByProjectIdOrderByDate(@Param("projectId") Long projectId);

    @Query("SELECT s FROM InterviewStep s WHERE s.project.freelance.id = :freelanceId AND s.date BETWEEN :startDate AND :endDate")
    List<InterviewStep> findByFreelanceIdAndDateBetween(@Param("freelanceId") Long freelanceId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT s FROM InterviewStep s WHERE s.project.freelance.id = :freelanceId AND s.status = :status")
    List<InterviewStep> findByFreelanceIdAndStatus(@Param("freelanceId") Long freelanceId, @Param("status") StepStatus status);

    @Query("SELECT COUNT(s) FROM InterviewStep s WHERE s.project.id = :projectId AND s.status = :status")
    Long countByProjectIdAndStatus(@Param("projectId") Long projectId, @Param("status") StepStatus status);
}
