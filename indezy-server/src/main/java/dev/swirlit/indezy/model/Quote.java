package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.QuoteStatus;
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
 * A quote (devis) of a workspace to one of its clients for fixed-price work: written as a draft, sent with the next
 * quote number of the year and the seller and buyer frozen as they are, then accepted or refused, and once accepted
 * billed by an invoice. Like an invoice, it holds the client and mission by id.
 */
@Entity
@Table(name = "quotes",
    indexes = @Index(name = "idx_quotes_freelance", columnList = "freelance_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_quotes_number", columnNames = {"freelance_id", "number"}))
@Getter
@Setter
public class Quote extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_role", length = 255)
    private String projectRole;

    /** Given when sent: "D", the year and a number that follows the last one with no gap ("D2026-0003"). */
    @Column(name = "number", length = 30)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QuoteStatus status = QuoteStatus.DRAFT;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    /** How many days the offer holds once sent. */
    @Column(name = "validity_days", nullable = false)
    private int validityDays = 30;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    /** The share of the total asked as a deposit on acceptance, in percent; none when absent. */
    @Column(name = "deposit_percent")
    private Integer depositPercent;

    /** The seller's terms and conditions when sent, printed after the quote; null for none. */
    @Column(name = "terms", columnDefinition = "TEXT")
    private String terms;

    /** The seller's payment term when sent, which the quote states for its invoice. */
    @Column(name = "payment_term_days")
    private Integer paymentTermDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 2)
    private DocumentLanguage language = DocumentLanguage.FR;

    @Column(name = "notes", length = 1000)
    private String notes;

    /** The day it was accepted or refused. */
    @Column(name = "decided_on")
    private LocalDate decidedOn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quote_lines", joinColumns = @JoinColumn(name = "quote_id"))
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
