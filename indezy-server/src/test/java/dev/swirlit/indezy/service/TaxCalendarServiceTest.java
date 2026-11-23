package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.FreelanceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaxCalendarServiceTest {

    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final RevenueCeilingService revenue = mock(RevenueCeilingService.class);
    private final TaxCalendarService calendar = new TaxCalendarService(freelances, revenue,
        Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC));
    private final BillingIdentity billing = new BillingIdentity();

    TaxCalendarServiceTest() {
        Freelance freelance = new Freelance();
        freelance.setBilling(billing);
        when(freelances.findById(7L)).thenReturn(Optional.of(freelance));
        when(revenue.payments(7L)).thenReturn(List.of(
            new RevenueCeilingService.Payment(LocalDate.of(2026, 3, 2), new BigDecimal("10000.00"), new BigDecimal("2000.00")),
            new RevenueCeilingService.Payment(LocalDate.of(2026, 9, 1), new BigDecimal("5000.00"), new BigDecimal("1000.00"))));
    }

    @Test
    void listsTheCfeAndTheSimplifiedVatCalendarWithTheInstalmentsEstimated() {
        billing.setVatExempt(false);

        List<TaxCalendarService.Deadline> deadlines = calendar.upcoming(7L);

        assertThat(deadlines).extracting(TaxCalendarService.Deadline::kind, TaxCalendarService.Deadline::dueOn).containsExactly(
            org.assertj.core.groups.Tuple.tuple(TaxCalendarService.Kind.CFE, LocalDate.of(2026, 12, 15)),
            org.assertj.core.groups.Tuple.tuple(TaxCalendarService.Kind.VAT_INSTALMENT, LocalDate.of(2026, 12, 31)),
            org.assertj.core.groups.Tuple.tuple(TaxCalendarService.Kind.VAT_RETURN, LocalDate.of(2027, 5, 4)),
            org.assertj.core.groups.Tuple.tuple(TaxCalendarService.Kind.VAT_INSTALMENT, LocalDate.of(2027, 7, 31)));
        // 2027's instalments come from 2026's 3 000 € of VAT received; December 2026's from 2025's, none.
        assertThat(deadlines.get(1).amount()).isEqualByComparingTo("0");
        assertThat(deadlines.get(2).amount()).isEqualByComparingTo("3000");
        assertThat(deadlines.get(3).amount()).isEqualByComparingTo("1650");
        assertThat(deadlines.get(3).monthOnly()).isTrue();
    }

    @Test
    void keepsTheCfeAloneUnderTheVatFranchiseAndMovesDaysOffToTheNextWorkingDay() {
        assertThat(calendar.upcoming(7L)).extracting(TaxCalendarService.Deadline::kind).containsExactly(TaxCalendarService.Kind.CFE);
        billing.setLegalForm(dev.swirlit.indezy.model.enums.LegalForm.PORTAGE);
        assertThat(calendar.upcoming(7L)).isEmpty();
        // 15 December 2029 is a Saturday; 1 May 2027 a Saturday, so the 3rd (Monday) and the 4th.
        assertThat(TaxCalendarService.workingDayFrom(LocalDate.of(2029, 12, 15))).isEqualTo(LocalDate.of(2029, 12, 17));
        assertThat(TaxCalendarService.secondWorkingDayAfterFirstMay(2027)).isEqualTo(LocalDate.of(2027, 5, 4));
        // 2026: 1 May a Friday; Monday 4 and Tuesday 5 May.
        assertThat(TaxCalendarService.secondWorkingDayAfterFirstMay(2026)).isEqualTo(LocalDate.of(2026, 5, 5));
    }
}
