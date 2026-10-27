package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * An invoice of a workspace to one of its clients. The workspace, client, mission and activity report are held by id,
 * not by relation: an issued invoice must be kept ten years, whatever happens to the mission or the client, and it states
 * the seller and buyer as they were when it was issued.
 */
@Entity
@Table(name = "invoices",
    indexes = @Index(name = "idx_invoices_freelance", columnList = "freelance_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_invoices_number", columnNames = {"freelance_id", "number"}))
@Getter
@Setter
public class Invoice extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "project_id")
    private Long projectId;

    /** The mission's role, kept for the list once the mission is gone. */
    @Column(name = "project_role", length = 255)
    private String projectRole;

    @Column(name = "activity_report_id")
    private Long activityReportId;

    /** Given when issued: prefix, year and a number that follows the last one with no gap ("F2026-0007"). */
    @Column(name = "number", length = 30)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 2)
    private DocumentLanguage language = DocumentLanguage.FR;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "invoice_lines", joinColumns = @JoinColumn(name = "invoice_id"))
    @OrderColumn(name = "position")
    private List<InvoiceLine> lines = new ArrayList<>();

    @Embedded
    private InvoiceSeller seller;

    @Embedded
    private InvoiceBuyer buyer;

    public List<InvoiceLine> getLines() {
        return lines != null ? new ArrayList<>(lines) : new ArrayList<>();
    }

    public void setLines(final List<InvoiceLine> lines) {
        this.lines = lines != null ? new ArrayList<>(lines) : new ArrayList<>();
    }
}
