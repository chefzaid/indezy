package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** The last invoice number a workspace gave in a year, locked while the next one is given so numbers never skip or repeat. */
@Entity
@Table(name = "invoice_counters",
    uniqueConstraints = @UniqueConstraint(name = "uk_invoice_counters", columnNames = {"freelance_id", "counter_year"}))
@Getter
@Setter
public class InvoiceCounter extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "counter_year", nullable = false)
    private int year;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;
}
