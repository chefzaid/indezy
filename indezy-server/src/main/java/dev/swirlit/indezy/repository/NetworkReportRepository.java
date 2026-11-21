package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkReport;
import dev.swirlit.indezy.model.enums.NetworkReportTarget;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NetworkReportRepository extends JpaRepository<NetworkReport, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndOutcomeIsNull(Long reporterId, NetworkReportTarget type, Long targetId);

    List<NetworkReport> findByTargetTypeAndTargetIdAndOutcomeIsNull(NetworkReportTarget type, Long targetId);

    /** Open reports, the oldest first. */
    List<NetworkReport> findByOutcomeIsNullOrderByCreatedAtAscIdAsc(Pageable page);

    /** Resolved reports, the latest decision first. */
    List<NetworkReport> findByOutcomeIsNotNullOrderByResolvedAtDescIdDesc(Pageable page);

    long countByOutcomeIsNull();

    /** The reports made since a day, for the dashboard. */
    List<NetworkReport> findByCreatedAtAfter(LocalDateTime since);

    long countByReporterIdAndCreatedAtAfter(Long reporterId, LocalDateTime since);
}
