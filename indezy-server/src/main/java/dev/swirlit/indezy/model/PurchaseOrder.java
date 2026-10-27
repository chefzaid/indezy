package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A client's purchase order for a mission, or an amendment to one: a ceiling in days or in euros (or both) over a period.
 * An amendment adds its days and euros to its order and may push its end back.
 */
@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
public class PurchaseOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** The order this amendment adds to; empty for an order. */
    @Column(name = "amends_id")
    private Long amendsId;

    @Column(name = "number", nullable = false, length = 60)
    private String number;

    /** When the order starts; an amendment has none. */
    @Column(name = "period_start")
    private LocalDate periodStart;

    /** When it ends: empty for an open order, or for an amendment that keeps its order's end. */
    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "day_ceiling", precision = 7, scale = 2)
    private BigDecimal dayCeiling;

    @Column(name = "amount_ceiling", precision = 12, scale = 2)
    private BigDecimal amountCeiling;

    @Column(name = "notes", length = 500)
    private String notes;

    public boolean isAmendment() {
        return amendsId != null;
    }
}
