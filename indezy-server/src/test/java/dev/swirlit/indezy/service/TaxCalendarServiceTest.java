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

    @Test
    void listsTheIncomeTaxInstalmentsOfAMicroEntrepriseUnlessPaidWithTheContributions() {
        billing.setLegalForm(dev.swirlit.indezy.model.enums.LegalForm.MICRO_ENTREPRISE);
        assertThat(calendar.upcoming(7L)).filteredOn(deadline -> deadline.kind() == TaxCalendarService.Kind.INCOME_TAX_INSTALMENT)
            .extracting(TaxCalendarService.Deadline::dueOn)
            .containsExactly(LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15), LocalDate.of(2027, 2, 15));
        billing.setIncomeTaxFrequency(dev.swirlit.indezy.model.enums.DeclarationFrequency.QUARTERLY);
        assertThat(calendar.upcoming(7L)).filteredOn(deadline -> deadline.kind() == TaxCalendarService.Kind.INCOME_TAX_INSTALMENT)
            .extracting(TaxCalendarService.Deadline::dueOn)
            .containsExactly(LocalDate.of(2027, 2, 15), LocalDate.of(2027, 5, 15), LocalDate.of(2027, 8, 15), LocalDate.of(2027, 11, 15));
        billing.setIncomeTaxDischarge(true);
        assertThat(calendar.upcoming(7L)).noneMatch(deadline -> deadline.kind() == TaxCalendarService.Kind.INCOME_TAX_INSTALMENT);
    }

    @Test
    void listsTheNormalRegimesMonthlyReturnsOnTheDayOfTheBusinessesPlaceAndName() {
        billing.setVatExempt(false);
        billing.setVatRegime(dev.swirlit.indezy.model.enums.VatRegime.NORMAL_MONTHLY);
        billing.setLegalForm(dev.swirlit.indezy.model.enums.LegalForm.EI);
        billing.setAddress("3 rue de Rivoli, 75004 Paris");
        freelances.findById(7L).orElseThrow().setLastName("Zola");

        List<TaxCalendarService.Deadline> returns = calendar.upcoming(7L).stream()
            .filter(deadline -> deadline.kind() == TaxCalendarService.Kind.VAT_PERIOD_RETURN).toList();

        // October's was due on 17 November; 17 January 2027 is a Sunday.
        assertThat(returns).extracting(TaxCalendarService.Deadline::dueOn)
            .containsExactly(LocalDate.of(2026, 12, 17), LocalDate.of(2027, 1, 18), LocalDate.of(2027, 2, 17));
        assertThat(returns.get(0).covers()).isEqualTo(java.time.YearMonth.of(2026, 11));
        assertThat(returns.get(0).amount()).isEqualByComparingTo("0");
        assertThat(returns.get(1).amount()).isNull();
        assertThat(calendar.upcoming(7L)).extracting(TaxCalendarService.Deadline::kind)
            .doesNotContain(TaxCalendarService.Kind.VAT_RETURN, TaxCalendarService.Kind.VAT_INSTALMENT);
    }

    @Test
    void listsTheQuarterlyReturnsOfACompanyAndOnlyTheirMonthWhenThePlaceIsUnknown() {
        billing.setVatExempt(false);
        billing.setVatRegime(dev.swirlit.indezy.model.enums.VatRegime.NORMAL_QUARTERLY);
        billing.setLegalForm(dev.swirlit.indezy.model.enums.LegalForm.SASU);
        billing.setAddress("10 quai Saint-Antoine 69002 Lyon");

        List<TaxCalendarService.Deadline> returns = calendar.upcoming(7L).stream()
            .filter(deadline -> deadline.kind() == TaxCalendarService.Kind.VAT_PERIOD_RETURN).toList();
        assertThat(returns).extracting(TaxCalendarService.Deadline::dueOn).containsExactly(LocalDate.of(2027, 1, 21),
            LocalDate.of(2027, 4, 21), LocalDate.of(2027, 7, 21), LocalDate.of(2027, 10, 21));
        assertThat(returns.get(0).months()).isEqualTo(3);
        assertThat(returns.get(0).covers()).isEqualTo(java.time.YearMonth.of(2026, 10));

        billing.setAddress(null);
        TaxCalendarService.Deadline unknown = calendar.upcoming(7L).stream()
            .filter(deadline -> deadline.kind() == TaxCalendarService.Kind.VAT_PERIOD_RETURN).findFirst().orElseThrow();
        assertThat(unknown.monthOnly()).isTrue();
        assertThat(unknown.dueOn()).isEqualTo(LocalDate.of(2027, 1, 31));
    }

    @Test
    void readsTheReturnsDayFromThePlaceTheFormAndTheNameOrSiren() {
        BillingIdentity identity = new BillingIdentity();
        identity.setLegalForm(dev.swirlit.indezy.model.enums.LegalForm.MICRO_ENTREPRISE);
        identity.setAddress("Bobigny 93000");
        assertThat(TaxCalendarService.returnDay(identity, "Évrard")).isEqualTo(15);
        assertThat(TaxCalendarService.returnDay(identity, "  ")).isNull();
        identity.setAddress("Rennes 35000");
        assertThat(TaxCalendarService.returnDay(identity, "Hugo")).isEqualTo(16);
        assertThat(TaxCalendarService.returnDay(identity, "Martin")).isEqualTo(19);
        identity.setLegalForm(dev.swirlit.indezy.model.enums.LegalForm.EURL);
        identity.setAddress("Nanterre 92000");
        identity.setSiret("123 456 789 00012");
        assertThat(TaxCalendarService.returnDay(identity, null)).isEqualTo(19);
        identity.setSiret("70000000000012");
        assertThat(TaxCalendarService.returnDay(identity, null)).isEqualTo(20);
        identity.setSiret("80000000000012");
        assertThat(TaxCalendarService.returnDay(identity, null)).isEqualTo(21);
        identity.setSiret(null);
        assertThat(TaxCalendarService.returnDay(identity, null)).isNull();
        identity.setLegalForm(dev.swirlit.indezy.model.enums.LegalForm.OTHER);
        assertThat(TaxCalendarService.returnDay(identity, "Martin")).isNull();
    }
}
