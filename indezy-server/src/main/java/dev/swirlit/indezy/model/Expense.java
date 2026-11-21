package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.ExpenseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A business expense of a workspace: when, what for, how much (VAT included) and its VAT, optionally for a mission. */
@Entity
@Table(name = "expenses", indexes = @Index(name = "idx_expenses_freelance_date", columnList = "freelance_id, spent_on"))
@Getter
@Setter
public class Expense extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private ExpenseCategory category;

    /** What was paid, VAT included. */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** The VAT it includes; zero when none. */
    @Column(name = "vat", precision = 12, scale = 2)
    private BigDecimal vat;

    /** The mission it was spent for, held by id like an invoice's, so deleting the mission keeps the expense. */
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_role", length = 255)
    private String projectRole;

    @Column(name = "notes", length = 500)
    private String notes;

    public BigDecimal getVat() {
        return vat == null ? BigDecimal.ZERO.setScale(2) : vat;
    }
}
