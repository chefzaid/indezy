package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.enums.InvoiceCurrency;
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
    void givesTheEurosOfAnInvoiceInAnotherCurrencyThatStillAddUp() {
        Invoice invoice = new Invoice();
        invoice.setLines(List.of(line("18.5", "633.33", "20"), line("1", "0.05", "5.5")));
        assertThat(InvoiceTotals.inEuros(invoice)).isEqualTo(InvoiceTotals.of(invoice.getLines()));

        invoice.setCurrency(InvoiceCurrency.CHF);
        assertThat(InvoiceTotals.inEuros(invoice)).isNull();
        invoice.setExchangeRate(new BigDecimal("1.063829"));
        InvoiceTotals euros = InvoiceTotals.inEuros(invoice);
        assertThat(euros.excludingVat()).isEqualByComparingTo("12464.52");
        assertThat(euros.total()).isEqualByComparingTo("14957.41");
        assertThat(euros.vat()).isEqualByComparingTo("2492.89");
    }

    @Test
    void sumsNothingToZero() {
        assertThat(InvoiceTotals.of(List.of()).total()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
