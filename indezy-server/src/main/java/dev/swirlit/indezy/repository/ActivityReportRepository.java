package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ActivityReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityReportRepository extends JpaRepository<ActivityReport, Long> {

    Optional<ActivityReport> findByProjectIdAndReportYearAndReportMonth(Long projectId, int reportYear, int reportMonth);

    /** A project's reports, latest month first. */
    List<ActivityReport> findByProjectIdOrderByReportYearDescReportMonthDesc(Long projectId);

    /** Every report of a workspace, for the account data export. */
    List<ActivityReport> findByProjectFreelanceIdOrderByReportYearDescReportMonthDesc(Long freelanceId);

    Optional<ActivityReport> findByValidationTokenHash(String validationTokenHash);

    /** Removes a project's reports, with their days, before the project itself is deleted. */
    void deleteByProjectId(Long projectId);
}
