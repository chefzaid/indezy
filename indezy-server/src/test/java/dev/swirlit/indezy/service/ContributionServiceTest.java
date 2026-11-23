package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.enums.DeclarationFrequency;
import dev.swirlit.indezy.model.enums.LegalForm;
import dev.swirlit.indezy.model.enums.MicroActivity;
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

class ContributionServiceTest {

    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final RevenueCeilingService revenue = mock(RevenueCeilingService.class);
    private final ContributionService service = new ContributionService(freelances, revenue,
        Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC));
    private final BillingIdentity billing = new BillingIdentity();

    ContributionServiceTest() {
        Freelance freelance = new Freelance();
        freelance.setBilling(billing);
        when(freelances.findById(7L)).thenReturn(Optional.of(freelance));
        when(revenue.payments(7L)).thenReturn(List.of(
            new RevenueCeilingService.Payment(LocalDate.of(2025, 12, 20), new BigDecimal("9000.00")),
            new RevenueCeilingService.Payment(LocalDate.of(2026, 2, 10), new BigDecimal("10000.00")),
            new RevenueCeilingService.Payment(LocalDate.of(2026, 3, 31), new BigDecimal("2000.00")),
            new RevenueCeilingService.Payment(LocalDate.of(2026, 10, 5), new BigDecimal("5000.00"))));
    }

    @Test
    void declaresEachQuarterAtTheOfficialRateWithItsDeadline() {
        billing.setLegalForm(LegalForm.MICRO_ENTREPRISE);
        billing.setDeclarationFrequency(DeclarationFrequency.QUARTERLY);

        ContributionService.Estimate estimate = service.estimate(7L, null);

        assertThat(estimate.rate()).isEqualByComparingTo("25.6");
        assertThat(estimate.officialRate()).isTrue();
        assertThat(estimate.revenue()).isEqualByComparingTo("17000");
        assertThat(estimate.contributions()).isEqualByComparingTo("4352");
        assertThat(estimate.periods()).extracting(ContributionService.Period::dueOn).containsExactly(LocalDate.of(2026, 4, 30),
            LocalDate.of(2026, 7, 31), LocalDate.of(2026, 10, 31), LocalDate.of(2027, 1, 31));
        assertThat(estimate.periods().getFirst().contributions()).isEqualByComparingTo("3072");
        assertThat(estimate.periods()).extracting(ContributionService.Period::state).containsExactly(ContributionService.PeriodState.PAST,
            ContributionService.PeriodState.PAST, ContributionService.PeriodState.PAST, ContributionService.PeriodState.RUNNING);
    }

    @Test
    void addsTheIncomeTaxPaidWithItAndListsTheMonthsSoFar() {
        billing.setLegalForm(LegalForm.MICRO_ENTREPRISE);
        billing.setMicroActivity(MicroActivity.BIC_SERVICES);
        billing.setIncomeTaxDischarge(true);

        ContributionService.Estimate estimate = service.estimate(7L, 2026);

        assertThat(estimate.rate()).isEqualByComparingTo("22.9");
        assertThat(estimate.periods()).hasSize(11);
        assertThat(estimate.periods().get(9)).satisfies(october -> {
            assertThat(october.dueOn()).isEqualTo(LocalDate.of(2026, 11, 30));
            assertThat(october.state()).isEqualTo(ContributionService.PeriodState.TO_DECLARE);
            assertThat(october.contributions()).isEqualByComparingTo("1145");
        });
        assertThat(estimate.periods().get(10).state()).isEqualTo(ContributionService.PeriodState.RUNNING);
    }

    @Test
    void usesTheMembersOwnRateAndKnowsNoneForACompanyWithout() {
        billing.setLegalForm(LegalForm.SASU);
        assertThat(service.estimate(7L, 2026)).satisfies(estimate -> {
            assertThat(estimate.rate()).isNull();
            assertThat(estimate.contributions()).isNull();
            assertThat(estimate.periods()).isEmpty();
        });
        billing.setOwnContributionRate(45.0);
        assertThat(service.estimate(7L, 2026)).satisfies(estimate -> {
            assertThat(estimate.contributions()).isEqualByComparingTo("7650");
            assertThat(estimate.officialRate()).isFalse();
        });
        // A year whose rates are not loaded yet has no official rate.
        billing.setLegalForm(LegalForm.MICRO_ENTREPRISE);
        billing.setOwnContributionRate(null);
        assertThat(service.estimate(7L, 2025).rate()).isNull();
    }
}
