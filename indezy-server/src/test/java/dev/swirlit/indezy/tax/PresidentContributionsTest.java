package dev.swirlit.indezy.tax;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PresidentContributionsTest {

    private static Map<PresidentContributions.Kind, PresidentContributions.Line> lines(PresidentContributions.Payslip payslip) {
        return payslip.lines().stream().collect(Collectors.toMap(PresidentContributions.Line::kind, line -> line));
    }

    @Test
    void computesThePayslipOfAPresidentPaidAboveOnePassWithTheCompanysAccidentRate() {
        PresidentContributions.Payslip payslip = PresidentContributions.of(new BigDecimal("60000"), new BigDecimal("0.80"));
        Map<PresidentContributions.Kind, PresidentContributions.Line> lines = lines(payslip);
        assertThat(lines.get(PresidentContributions.Kind.HEALTH).employer()).isEqualByComparingTo("7800");
        assertThat(lines.get(PresidentContributions.Kind.PENSION_CAPPED).employee()).isEqualByComparingTo("3316.14");
        // 98.25 % of the pay: 58,950 € at 9.7 %.
        assertThat(lines.get(PresidentContributions.Kind.CSG_CRDS).employee()).isEqualByComparingTo("5718.15");
        // T1 48,060 € at 4.72 % and T2 11,940 € at 12.95 %.
        assertThat(lines.get(PresidentContributions.Kind.AGIRC_ARRCO).employer()).isEqualByComparingTo("3814.66");
        assertThat(lines.get(PresidentContributions.Kind.ACCIDENTS).employer()).isEqualByComparingTo("480");
        assertThat(lines).containsKey(PresidentContributions.Kind.TECHNICAL_BALANCE);
        assertThat(payslip.employer()).isEqualByComparingTo("21796.85");
        assertThat(payslip.employee()).isEqualByComparingTo("12446.07");
    }

    @Test
    void leavesOutTheAccidentsWithoutTheirRateAndTheTechnicalBalanceUnderOnePass() {
        Map<PresidentContributions.Kind, PresidentContributions.Line> lines = lines(PresidentContributions.of(new BigDecimal("30000"), null));
        assertThat(lines).doesNotContainKeys(PresidentContributions.Kind.ACCIDENTS, PresidentContributions.Kind.TECHNICAL_BALANCE);
        assertThat(lines.get(PresidentContributions.Kind.BALANCE).employee()).isEqualByComparingTo("258.00");
        assertThat(PresidentContributions.of(new BigDecimal("-10"), null).employee()).isEqualByComparingTo("0");
    }
}
