package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.InvoiceLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceTotalsTest {

    private static InvoiceLine line(String quantity, String price, String vat) {
        return new InvoiceLine("Prestation", new BigDecimal(quantity), new BigDecimal(price), new BigDecimal(vat));
    }

    @Test
    void roundsEachLineToTheCentThenAddsItsVat() {
        InvoiceTotals totals = InvoiceTotals.of(List.of(line("18.5", "633.33", "20"), line("1", "0.05", "5.5")));

        assertThat(totals.excludingVat()).isEqualByComparingTo("11716.66");
        assertThat(totals.vat()).isEqualByComparingTo("2343.32");
        assertThat(totals.total()).isEqualByComparingTo("14059.98");
        assertThat(InvoiceTotals.lineAmount(line("0.5", "0.01", "0"))).isEqualByComparingTo("0.01");
    }

    @Test
    void sumsNothingToZero() {
        assertThat(InvoiceTotals.of(List.of()).total()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
