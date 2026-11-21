package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.ExpenseCategory;
import dev.swirlit.indezy.model.enums.ExpenseTreatment;
import dev.swirlit.indezy.model.enums.LegalForm;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** A workspace's business expenses and what they change in its net income. */
public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    /** An expense as written: when, what for, how much VAT included, its VAT, optionally its mission and notes. */
    public record Draft(
        @NotNull LocalDate spentOn,
        @NotBlank @Size(max = 200) String label,
        @NotNull ExpenseCategory category,
        @NotNull @DecimalMin("0.01") @DecimalMax("999999.99") @Digits(integer = 6, fraction = 2) BigDecimal amount,
        @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal vat,
        Long projectId,
        @Size(max = 500) String notes) {
    }

    public record Expense(Long id, LocalDate spentOn, String label, ExpenseCategory category, BigDecimal amount, BigDecimal vat,
                          Long projectId, String projectRole, String notes) {
    }

    /**
     * A year's expenses: their total VAT included and their VAT, by month and by category, and what they change in the
     * net income under the workspace's status. {@code deductible} is what lowers the taxable profit or is reimbursed
     * (the total less the VAT recovered when the seller charges VAT; zero in a micro-entreprise), {@code netCost} what
     * they cost once charges and tax are counted, at the reversion rate.
     */
    public record Summary(int year, LegalForm legalForm, ExpenseTreatment treatment, BigDecimal total, BigDecimal vat,
                          BigDecimal deductible, BigDecimal netCost, double reversionRate, List<BigDecimal> months,
                          Map<ExpenseCategory, BigDecimal> byCategory) {

        public Summary {
            months = List.copyOf(months);
            byCategory = Map.copyOf(byCategory);
        }
    }
}
