package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.enums.DeclarationFrequency;
import dev.swirlit.indezy.model.enums.LegalForm;
import dev.swirlit.indezy.model.enums.MicroActivity;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.tax.FrenchFigures;
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

/**
 * The URSSAF contributions on the revenue received (see docs/features.md#urssaf-contributions): for a micro-entreprise,
 * each month or quarter's revenue times the official rate of its activity (the income tax added when paid with it), or
 * the member's own rate, with the day each declaration is due; for another legal form, the member's own rate on the
 * year's revenue, the official rates being for the micro-entreprise only.
 */
@Service
@RequiredArgsConstructor
public class ContributionService {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final FreelanceRepository freelances;
    private final RevenueCeilingService revenue;
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
     * micro-entreprise only, else empty).
     */
    public record Estimate(int year, int figuresYear, LegalForm legalForm, MicroActivity activity, boolean incomeTaxDischarge,
                           DeclarationFrequency frequency, BigDecimal rate, boolean officialRate, BigDecimal revenue,
                           BigDecimal contributions, List<Period> periods) {

        public Estimate {
            periods = List.copyOf(periods);
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
        return new Estimate(year, figures.year(), form, activity, discharge, frequency, rate, own == null && official != null, received,
            contributions(received, rate), periods);
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
