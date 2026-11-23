package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.util.FrenchPublicHolidays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The tax deadlines beside the URSSAF declarations (see docs/features.md#tax-calendar), the next twelve months: the CFE
 * by 15 December, and, while charging VAT under the simplified regime with a year closed on 31 December, its two
 * instalments (55 % in July and 40 % in December of the VAT of the year before) and the annual return with the balance
 * by the second working day after 1 May, each instalment estimated from the VAT on the payments received, before the VAT
 * on expenses. Dates from service-public.gouv.fr (checked 2026-11-23); a day falling on a weekend or a public holiday
 * moves to the next working day.
 */
@Service
@RequiredArgsConstructor
public class TaxCalendarService {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final BigDecimal JULY_SHARE = new BigDecimal("0.55");
    private static final BigDecimal DECEMBER_SHARE = new BigDecimal("0.40");
    private static final int CFE_DAY = 15;
    private static final int MONTHS = 12;

    private final FreelanceRepository freelances;
    private final RevenueCeilingService revenue;
    private final Clock clock;

    /** What is due: the CFE, a VAT instalment or the annual VAT return. */
    public enum Kind {
        CFE,
        VAT_INSTALMENT,
        VAT_RETURN
    }

    /**
     * A deadline: what, the day (for a VAT instalment, the last day of its month: the notice gives the day), whether only
     * the month is known, and the amount estimated, null when Indezy cannot estimate it (the CFE).
     */
    public record Deadline(Kind kind, LocalDate dueOn, boolean monthOnly, BigDecimal amount) {
    }

    @Transactional(readOnly = true)
    public List<Deadline> upcoming(Long freelanceId) {
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        LocalDate until = today.plusMonths(MONTHS);
        BillingIdentity billing = freelances.findById(freelanceId).map(Freelance::getBilling).orElseGet(BillingIdentity::new);
        boolean chargesVat = Boolean.FALSE.equals(billing.getVatExempt());
        List<RevenueCeilingService.Payment> payments = chargesVat ? revenue.payments(freelanceId) : List.of();
        List<Deadline> deadlines = new ArrayList<>();
        // A portage employee owes no CFE: the portage company does.
        boolean cfe = billing.getLegalForm() != dev.swirlit.indezy.model.enums.LegalForm.PORTAGE;
        for (int year = today.getYear(); year <= until.getYear(); year++) {
            if (cfe) {
                deadlines.add(new Deadline(Kind.CFE, workingDayFrom(LocalDate.of(year, Month.DECEMBER, CFE_DAY)), false, null));
            }
            if (chargesVat) {
                BigDecimal lastYear = vat(payments, year - 1);
                deadlines.add(new Deadline(Kind.VAT_RETURN, secondWorkingDayAfterFirstMay(year), false, lastYear));
                deadlines.add(new Deadline(Kind.VAT_INSTALMENT, YearMonth.of(year, Month.JULY).atEndOfMonth(), true, share(lastYear, JULY_SHARE)));
                deadlines.add(new Deadline(Kind.VAT_INSTALMENT, YearMonth.of(year, Month.DECEMBER).atEndOfMonth(), true,
                    share(lastYear, DECEMBER_SHARE)));
            }
        }
        return deadlines.stream().filter(deadline -> !deadline.dueOn().isBefore(today) && !deadline.dueOn().isAfter(until))
            .sorted(Comparator.comparing(Deadline::dueOn)).toList();
    }

    private static BigDecimal vat(List<RevenueCeilingService.Payment> payments, int year) {
        return payments.stream().filter(payment -> payment.on().getYear() == year).map(RevenueCeilingService.Payment::vat)
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal share(BigDecimal amount, BigDecimal share) {
        return amount.multiply(share).setScale(2, RoundingMode.HALF_UP);
    }

    /** The day itself when it is a working day, else the next one. */
    static LocalDate workingDayFrom(LocalDate day) {
        LocalDate date = day;
        while (!working(date)) {
            date = date.plusDays(1);
        }
        return date;
    }

    /** The second working day after 1 May (the annual VAT return of the simplified regime). */
    static LocalDate secondWorkingDayAfterFirstMay(int year) {
        LocalDate date = LocalDate.of(year, Month.MAY, 1);
        int found = 0;
        while (found < 2) {
            date = date.plusDays(1);
            if (working(date)) {
                found++;
            }
        }
        return date;
    }

    private static boolean working(LocalDate date) {
        return !FrenchPublicHolidays.isWeekend(date) && !FrenchPublicHolidays.of(date.getYear()).containsKey(date);
    }
}
