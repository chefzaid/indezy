package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ExpenseDtos;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Expense;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ExpenseCategory;
import dev.swirlit.indezy.model.enums.ExpenseTreatment;
import dev.swirlit.indezy.model.enums.LegalForm;
import dev.swirlit.indezy.repository.ExpenseRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A workspace's business expenses, and what they change in its net income by its status: nothing in a micro-entreprise
 * (the flat allowance counts them), a lower taxable profit in an EI au réel or a company, a reimbursement before charges
 * in portage.
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    /** The share of the gross a freelancer keeps when the profile sets none, as the opportunities list assumes. */
    static final double DEFAULT_REVERSION_RATE = 55;
    private static final int MONTHS = 12;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ExpenseRepository expenses;
    private final FreelanceRepository freelances;
    private final ProjectRepository projects;

    /** The workspace's expenses of a year, the latest first, or all of them. */
    @Transactional(readOnly = true)
    public List<ExpenseDtos.Expense> list(Long freelanceId, Integer year) {
        List<Expense> found = year == null ? expenses.findByFreelanceIdOrderBySpentOnDescIdDesc(freelanceId) :
            expenses.findByFreelanceIdAndSpentOnBetweenOrderBySpentOnDescIdDesc(freelanceId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        return found.stream().map(ExpenseService::toDto).toList();
    }

    @Transactional
    public ExpenseDtos.Expense create(Long freelanceId, ExpenseDtos.Draft draft) {
        Expense expense = new Expense();
        expense.setFreelanceId(freelanceId);
        write(expense, draft);
        return toDto(expenses.save(expense));
    }

    @Transactional
    public ExpenseDtos.Expense update(Long freelanceId, Long id, ExpenseDtos.Draft draft) {
        Expense expense = expense(freelanceId, id);
        write(expense, draft);
        return toDto(expense);
    }

    @Transactional
    public void delete(Long freelanceId, Long id) {
        expenses.delete(expense(freelanceId, id));
    }

    /** The year's totals by month and category, and what they change in the net income under the workspace's status. */
    @Transactional(readOnly = true)
    public ExpenseDtos.Summary summary(Long freelanceId, int year) {
        Freelance freelance = freelances.findById(freelanceId).orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        BillingIdentity billing = Objects.requireNonNullElseGet(freelance.getBilling(), BillingIdentity::new);
        LegalForm form = billing.getLegalForm();
        ExpenseTreatment treatment = ExpenseTreatment.of(form);
        boolean recoversVat = Boolean.FALSE.equals(billing.getVatExempt());
        double reversion = Objects.requireNonNullElse(freelance.getReversionRate(), DEFAULT_REVERSION_RATE);

        List<BigDecimal> months = new ArrayList<>();
        for (int month = 0; month < MONTHS; month++) {
            months.add(BigDecimal.ZERO.setScale(2));
        }
        Map<ExpenseCategory, BigDecimal> byCategory = new EnumMap<>(ExpenseCategory.class);
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        BigDecimal vat = BigDecimal.ZERO.setScale(2);
        for (Expense expense : expenses.findByFreelanceIdAndSpentOnBetweenOrderBySpentOnDescIdDesc(freelanceId, LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31))) {
            int month = expense.getSpentOn().getMonthValue() - 1;
            months.set(month, months.get(month).add(expense.getAmount()));
            byCategory.merge(expense.getCategory(), expense.getAmount(), BigDecimal::add);
            total = total.add(expense.getAmount());
            vat = vat.add(expense.getVat());
        }
        // Without VAT to charge, the VAT paid is not recovered: it is part of the cost.
        BigDecimal excludingRecovered = recoversVat ? total.subtract(vat) : total;
        BigDecimal deductible = switch (treatment) {
            case DEDUCTIBLE, REIMBURSED -> excludingRecovered;
            case FLAT_ALLOWANCE, RECORDED -> BigDecimal.ZERO.setScale(2);
        };
        BigDecimal netCost = switch (treatment) {
            // What is deducted or reimbursed would otherwise have been paid out at the reversion rate.
            case DEDUCTIBLE, REIMBURSED -> excludingRecovered.multiply(BigDecimal.valueOf(reversion)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            case FLAT_ALLOWANCE, RECORDED -> excludingRecovered;
        };
        return new ExpenseDtos.Summary(year, form, treatment, total, vat, deductible, netCost, reversion, months, byCategory);
    }

    private Expense expense(Long freelanceId, Long id) {
        return expenses.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
    }

    private void write(Expense expense, ExpenseDtos.Draft draft) {
        BigDecimal vat = Objects.requireNonNullElse(draft.vat(), BigDecimal.ZERO);
        if (vat.compareTo(draft.amount()) > 0) {
            throw new IllegalArgumentException("The VAT cannot exceed the amount it is part of");
        }
        expense.setSpentOn(draft.spentOn());
        expense.setLabel(draft.label().strip());
        expense.setCategory(draft.category());
        expense.setAmount(draft.amount().setScale(2, RoundingMode.HALF_UP));
        expense.setVat(vat.setScale(2, RoundingMode.HALF_UP));
        expense.setNotes(draft.notes() == null || draft.notes().isBlank() ? null : draft.notes().strip());
        if (draft.projectId() == null) {
            expense.setProjectId(null);
            expense.setProjectRole(null);
        } else {
            Project project = projects.findById(draft.projectId()).filter(found -> found.getFreelance() != null &&
                    expense.getFreelanceId().equals(found.getFreelance().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
            expense.setProjectId(project.getId());
            expense.setProjectRole(project.getRole());
        }
    }

    static ExpenseDtos.Expense toDto(Expense expense) {
        return new ExpenseDtos.Expense(expense.getId(), expense.getSpentOn(), expense.getLabel(), expense.getCategory(), expense.getAmount(),
            expense.getVat(), expense.getProjectId(), expense.getProjectRole(), expense.getNotes());
    }
}
