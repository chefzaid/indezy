package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.enums.InvoiceCurrency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** An invoice's amounts: each line's price rounded to the cent, VAT per line, then summed. */
public record InvoiceTotals(BigDecimal excludingVat, BigDecimal vat, BigDecimal total) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static InvoiceTotals of(List<InvoiceLine> lines) {
        BigDecimal excluding = BigDecimal.ZERO.setScale(2);
        BigDecimal vat = BigDecimal.ZERO.setScale(2);
        for (InvoiceLine line : lines) {
            BigDecimal amount = lineAmount(line);
            excluding = excluding.add(amount);
            vat = vat.add(amount.multiply(line.getVatRate()).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        }
        return new InvoiceTotals(excluding, vat, excluding.add(vat));
    }

    /**
     * The invoice's amounts in euros: as they are for an invoice in euros, otherwise its totals at its rate to the cent,
     * the VAT being what the total leaves over the amount before VAT so that the three still add up; null while an
     * invoice in another currency has no rate.
     */
    public static InvoiceTotals inEuros(Invoice invoice) {
        InvoiceTotals totals = of(invoice.getLines());
        if (invoice.getCurrency() == InvoiceCurrency.EUR) {
            return totals;
        }
        if (invoice.getExchangeRate() == null) {
            return null;
        }
        BigDecimal excluding = euros(totals.excludingVat(), invoice.getExchangeRate());
        BigDecimal total = euros(totals.total(), invoice.getExchangeRate());
        return new InvoiceTotals(excluding, total.subtract(excluding), total);
    }

    /** An amount of the invoice's currency in euros, to the cent. */
    public static BigDecimal euros(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    /** Quantity times unit price, to the cent. */
    public static BigDecimal lineAmount(InvoiceLine line) {
        return line.getQuantity().multiply(line.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
    }
}
