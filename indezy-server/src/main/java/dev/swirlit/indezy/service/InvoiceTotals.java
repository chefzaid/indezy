package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.InvoiceLine;

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

    /** Quantity times unit price, to the cent. */
    public static BigDecimal lineAmount(InvoiceLine line) {
        return line.getQuantity().multiply(line.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
    }
}
