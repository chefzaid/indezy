package dev.swirlit.indezy.tax;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class IndependentContributionsTest {

    private static Map<IndependentContributions.Kind, BigDecimal> lines(IndependentContributions.Contributions contributions) {
        return contributions.lines().stream().collect(Collectors.toMap(IndependentContributions.Line::kind, IndependentContributions.Line::amount));
    }

    @Test
    void computesEachLineOnTheProfitLessTheAbatementUnderOnePass() {
        IndependentContributions.Contributions contributions = IndependentContributions.of(new BigDecimal("60000")).orElseThrow();
        assertThat(contributions.base()).isEqualByComparingTo("44400");
        Map<IndependentContributions.Kind, BigDecimal> lines = lines(contributions);
        // Health: 4 % + 2.5 × (44,400 − 0.6 PASS) / (0.5 PASS) = 5.6192 %.
        assertThat(lines.get(IndependentContributions.Kind.HEALTH)).isEqualByComparingTo("2494.94");
        assertThat(lines.get(IndependentContributions.Kind.DAILY_ALLOWANCES)).isEqualByComparingTo("222.00");
        assertThat(lines.get(IndependentContributions.Kind.BASE_PENSION)).isEqualByComparingTo("8253.96");
        assertThat(lines.get(IndependentContributions.Kind.SUPPLEMENTARY_PENSION)).isEqualByComparingTo("3596.40");
        assertThat(lines.get(IndependentContributions.Kind.DISABILITY)).isEqualByComparingTo("577.20");
        assertThat(lines.get(IndependentContributions.Kind.FAMILY)).isEqualByComparingTo("0");
        assertThat(lines.get(IndependentContributions.Kind.CSG_CRDS)).isEqualByComparingTo("4306.80");
        assertThat(lines.get(IndependentContributions.Kind.TRAINING)).isEqualByComparingTo("120.15");
        assertThat(contributions.total()).isEqualByComparingTo("19571.45");
    }

    @Test
    void capsThePensionsAndTheFamilyRateAboveOnePassAndStopsPastThree() {
        Map<IndependentContributions.Kind, BigDecimal> lines = lines(IndependentContributions.of(new BigDecimal("100000")).orElseThrow());
        // Base 74,000: health 6.5 % + 1.2 × (base − 1.1 PASS) / (0.9 PASS); family at its 3.1 % from 1.4 PASS.
        assertThat(lines.get(IndependentContributions.Kind.HEALTH)).isEqualByComparingTo("5243.88");
        assertThat(lines.get(IndependentContributions.Kind.FAMILY)).isEqualByComparingTo("2294.00");
        assertThat(lines.get(IndependentContributions.Kind.BASE_PENSION)).isEqualByComparingTo("9121.12");
        assertThat(lines.get(IndependentContributions.Kind.SUPPLEMENTARY_PENSION)).isEqualByComparingTo("6253.40");
        assertThat(lines.get(IndependentContributions.Kind.DISABILITY)).isEqualByComparingTo("624.78");

        assertThat(IndependentContributions.healthRate(new BigDecimal("9000"), IndependentContributions.PASS_2026)).isEqualByComparingTo("0");
        assertThat(IndependentContributions.healthRate(new BigDecimal("144180"), IndependentContributions.PASS_2026)).isEqualByComparingTo("8.5");
        assertThat(IndependentContributions.familyRate(new BigDecimal("60000"), IndependentContributions.PASS_2026)).isPositive();
        assertThat(IndependentContributions.of(new BigDecimal("200000"))).isEmpty();
        // A loss: nothing on the base, the training contribution alone.
        assertThat(IndependentContributions.of(new BigDecimal("-500")).orElseThrow().total()).isEqualByComparingTo("120.15");
    }
}
