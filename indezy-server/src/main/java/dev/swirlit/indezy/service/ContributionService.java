package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.enums.DeclarationFrequency;
import dev.swirlit.indezy.model.enums.LegalForm;
import dev.swirlit.indezy.model.enums.MicroActivity;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.tax.FrenchFigures;
import dev.swirlit.indezy.tax.IndependentContributions;
import dev.swirlit.indezy.tax.PresidentContributions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The URSSAF contributions on the revenue received (see docs/features.md#urssaf-contributions): for a micro-entreprise,
 * each month or quarter's revenue times the official rate of its activity (the income tax added when paid with it), or
 * the member's own rate, with the day each declaration is due; for an EI under the real regime affiliated with the SSI
 * (not the CIPAV), the official contributions on the year's profit so far, line by line ({@link IndependentContributions},
 * the revenue received less the deductible expenses); for an EURL's majority manager, the same on the year's pay they
 * give, with their dividends past 10 % of the share capital; for a SASU president, the payroll's contributions on the
 * gross pay they give ({@link PresidentContributions}), the company's share and theirs; for another legal form, the
 * member's own rate on the year's revenue.
 */
@Service
@RequiredArgsConstructor
public class ContributionService {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final FreelanceRepository freelances;
    private final RevenueCeilingService revenue;
    private final ExpenseService expenses;
    private final Clock clock;

    /** Where a declaration stands: its period still running, ended and to declare, or past its deadline. */
    public enum PeriodState {
        RUNNING,
        TO_DECLARE,
        PAST
    }

    /** One month or quarter: its first and last day, the revenue received in it, the contributions on it and their deadline. */
    public record Period(LocalDate from, LocalDate to, BigDecimal revenue, BigDecimal contributions, LocalDate dueOn, PeriodState state) {
    }

    /**
     * The year's estimate: the rate applied and whether it is the official one (null when neither is known), the
     * figures' year, the settings it follows, the year's revenue and contributions, and the declarations (a
     * micro-entreprise only, else empty); for an EI under the real regime, the profit, the base after the abatement
     * and the contributions line by line (else null and empty; the profit without a base when past the table's three
     * PASS, no rate then); for a SASU president, the gross pay as {@code profit}
     * and the payslip's lines with the company's and the president's totals (else empty and null).
     */
    public record Estimate(int year, int figuresYear, LegalForm legalForm, MicroActivity activity, boolean incomeTaxDischarge,
                           DeclarationFrequency frequency, BigDecimal rate, boolean officialRate, BigDecimal revenue,
                           BigDecimal contributions, List<Period> periods, BigDecimal profit, BigDecimal base,
                           List<IndependentContributions.Line> lines, List<PresidentContributions.Line> payroll,
                           BigDecimal employerShare, BigDecimal employeeShare) {

        public Estimate {
            periods = List.copyOf(periods);
            lines = List.copyOf(lines);
            payroll = List.copyOf(payroll);
        }
    }

    @Transactional(readOnly = true)
    public Estimate estimate(Long freelanceId, Integer asked) {
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        int year = asked != null ? asked : today.getYear();
        BillingIdentity billing = freelances.findById(freelanceId).map(Freelance::getBilling).orElseGet(BillingIdentity::new);
        LegalForm form = billing.getLegalForm();
        MicroActivity activity = Objects.requireNonNullElse(billing.getMicroActivity(), MicroActivity.BNC_SSI);
        boolean discharge = Boolean.TRUE.equals(billing.getIncomeTaxDischarge());
        DeclarationFrequency frequency = Objects.requireNonNullElse(billing.getDeclarationFrequency(), DeclarationFrequency.MONTHLY);
        FrenchFigures.Year figures = FrenchFigures.of(year);
        boolean micro = form == LegalForm.MICRO_ENTREPRISE;
        BigDecimal own = billing.getOwnContributionRate() == null ? null : BigDecimal.valueOf(billing.getOwnContributionRate());
        BigDecimal official = micro && figures.year() == year ? figures.rate(activity, discharge) : null;
        BigDecimal rate = own != null ? own : official;
        List<RevenueCeilingService.Payment> payments = revenue.payments(freelanceId).stream()
            .filter(payment -> payment.on().getYear() == year).toList();
        BigDecimal received = sum(payments, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        List<Period> periods = new ArrayList<>();
        if (micro) {
            int months = frequency == DeclarationFrequency.QUARTERLY ? 3 : 1;
            for (int first = 1; first <= 12; first += months) {
                LocalDate from = LocalDate.of(year, first, 1);
                LocalDate to = YearMonth.of(year, first + months - 1).atEndOfMonth();
                LocalDate due = dueOn(to);
                BigDecimal amount = sum(payments, from, to);
                PeriodState state = today.isAfter(due) ? PeriodState.PAST : today.isAfter(to) ? PeriodState.TO_DECLARE : PeriodState.RUNNING;
                if (!from.isAfter(today)) {
                    periods.add(new Period(from, to, amount, contributions(amount, rate), due, state));
                }
            }
        }
        boolean independent = form == LegalForm.EI || form == LegalForm.EURL && billing.getManagerPay() != null;
        if (independent && own == null && year == IndependentContributions.YEAR && activity != MicroActivity.BNC_CIPAV) {
            BigDecimal profit = form == LegalForm.EI ? received.subtract(expenses.summary(freelanceId, year).deductible()) : managerIncome(billing);
            Optional<IndependentContributions.Contributions> official2026 = IndependentContributions.of(profit);
            if (official2026.isPresent()) {
                IndependentContributions.Contributions computed = official2026.get();
                BigDecimal share = received.signum() == 0 ? null : computed.total().multiply(HUNDRED).divide(received, 2, RoundingMode.HALF_UP);
                return new Estimate(year, IndependentContributions.YEAR, form, activity, discharge, frequency, share, true, received,
                    computed.total(), List.of(), profit, computed.base(), computed.lines(), List.of(), null, null);
            }
            // Past three PASS, which the official table stops at: the profit alone, the member asked for their own rate.
            return new Estimate(year, IndependentContributions.YEAR, form, activity, discharge, frequency, null, false, received, null,
                List.of(), profit, null, List.of(), List.of(), null, null);
        }
        if (form == LegalForm.SASU && own == null && year == IndependentContributions.YEAR && billing.getManagerPay() != null) {
            BigDecimal gross = BigDecimal.valueOf(billing.getManagerPay()).setScale(2, RoundingMode.HALF_UP);
            PresidentContributions.Payslip payslip = PresidentContributions.of(gross,
                billing.getAccidentRate() == null ? null : BigDecimal.valueOf(billing.getAccidentRate()));
            BigDecimal total = payslip.employer().add(payslip.employee());
            BigDecimal share = received.signum() == 0 ? null : total.multiply(HUNDRED).divide(received, 2, RoundingMode.HALF_UP);
            return new Estimate(year, IndependentContributions.YEAR, form, activity, discharge, frequency, share, true, received, total,
                List.of(), gross, null, List.of(), payslip.lines(), payslip.employer(), payslip.employee());
        }
        return new Estimate(year, figures.year(), form, activity, discharge, frequency, rate, own == null && official != null, received,
            contributions(received, rate), periods, null, null, List.of(), List.of(), null, null);
    }

    /** An EURL manager's income for the contributions: their pay, and their dividends past 10 % of the share capital. */
    static BigDecimal managerIncome(BillingIdentity billing) {
        BigDecimal pay = BigDecimal.valueOf(Objects.requireNonNullElse(billing.getManagerPay(), 0));
        BigDecimal dividends = BigDecimal.valueOf(Objects.requireNonNullElse(billing.getManagerDividends(), 0));
        BigDecimal free = BigDecimal.valueOf(Objects.requireNonNullElse(billing.getShareCapital(), 0)).divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);
        return pay.add(dividends.subtract(free).max(BigDecimal.ZERO)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * The day a period's declaration is due: the end of the next month when declaring monthly; 30 April, 31 July,
     * 31 October and 31 January when declaring quarterly, which is the end of the month after the quarter too.
     */
    static LocalDate dueOn(LocalDate periodEnd) {
        return YearMonth.from(periodEnd).plusMonths(1).atEndOfMonth();
    }

    private static BigDecimal sum(List<RevenueCeilingService.Payment> payments, LocalDate from, LocalDate to) {
        return payments.stream().filter(payment -> !payment.on().isBefore(from) && !payment.on().isAfter(to))
            .map(RevenueCeilingService.Payment::amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal contributions(BigDecimal amount, BigDecimal rate) {
        return rate == null ? null : amount.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
