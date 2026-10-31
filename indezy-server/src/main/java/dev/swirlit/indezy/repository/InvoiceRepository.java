package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByFreelanceIdOrderByCreatedAtDesc(Long freelanceId);

    List<Invoice> findByFreelanceIdAndStatus(Long freelanceId, InvoiceStatus status);

    Optional<Invoice> findByIdAndFreelanceId(Long id, Long freelanceId);

    Optional<Invoice> findFirstByActivityReportId(Long activityReportId);

    List<Invoice> findByCreditedInvoiceIdAndNumberIsNotNull(Long creditedInvoiceId);

    List<Invoice> findByFreelanceIdAndStatusAndDueDateBeforeOrderByDueDateAsc(Long freelanceId, InvoiceStatus status, LocalDate before);

    /** The date of the workspace's latest issued invoice. */
    @Query("SELECT MAX(i.issueDate) FROM Invoice i WHERE i.freelanceId = :freelanceId AND i.issueDate IS NOT NULL")
    Optional<LocalDate> findLatestIssueDate(@Param("freelanceId") Long freelanceId);
}
