package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.Quote;
import dev.swirlit.indezy.model.enums.QuoteStatus;
import dev.swirlit.indezy.service.InvoiceTotals;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays out a quote: the seller and the quote's number and dates, the client, the lines and the totals, then its terms
 * (VAT exemption, how long the offer holds, the deposit asked, the payment term, free of charge) and a box for the
 * client's "Bon pour accord". A draft says it has not been sent.
 */
final class QuotePage extends BillingPage {

    private static final float SIGNATURE_BOX = 70;

    private final Quote quote;
    private static final float TERMS_TITLE_SIZE = 12f;
    private final Integer paymentTermDays;
    /** The seller's terms and conditions, printed on pages after the quote; null for none. */
    private final String terms;
    /** Whether a terms and conditions PDF follows the quote. */
    private final boolean termsFileAttached;

    QuotePage(PdfPageWriter writer, Quote quote, InvoiceSeller seller, InvoiceBuyer buyer, Integer paymentTermDays, String terms,
              boolean termsFileAttached) {
        super(writer, seller, buyer, quote.getLanguage());
        this.quote = quote;
        this.paymentTermDays = paymentTermDays;
        this.terms = terms == null || terms.isBlank() ? null : terms.strip();
        this.termsFileAttached = termsFileAttached;
    }

    String title() {
        return QuoteLabel.TITLE.in(language) + (quote.getNumber() == null ? "" : " " + quote.getNumber());
    }

    void write() throws IOException {
        header(this::quoteBlock, QuoteLabel.FOR.in(language));
        lines(quote.getLines());
        InvoiceTotals totals = InvoiceTotals.of(quote.getLines());
        totals(totals, (vatExempt() ? InvoiceLabel.TOTAL_EXEMPT : InvoiceLabel.TOTAL).in(language));
        mentions(mentions(totals.total()), quote.getNotes());
        acceptance();
        if (terms != null) {
            writer.startPage();
            writer.paragraph(QuoteLabel.TERMS_TITLE.in(language), true, TERMS_TITLE_SIZE, writer.width());
            writer.moveDown(LINE);
            writer.paragraph(terms, false, SMALL, writer.width());
        }
    }

    private void quoteBlock() throws IOException {
        List<String> lines = new ArrayList<>();
        if (quote.getNumber() != null) {
            lines.add(InvoiceLabel.NUMBER.format(language, quote.getNumber()));
        }
        if (quote.getIssueDate() != null) {
            lines.add(QuoteLabel.ISSUED.format(language, date(quote.getIssueDate())));
        }
        if (quote.getValidUntil() != null) {
            lines.add(QuoteLabel.VALID_UNTIL.format(language, date(quote.getValidUntil())));
        }
        headingBlock(QuoteLabel.TITLE.in(language), lines, quote.getStatus() == QuoteStatus.DRAFT ? QuoteLabel.DRAFT.in(language) : null);
    }

    private List<String> mentions(BigDecimal total) {
        List<String> mentions = new ArrayList<>();
        if (vatExempt()) {
            mentions.add(InvoiceLabel.VAT_EXEMPT.in(language));
        }
        mentions.add(quote.getValidUntil() == null ? QuoteLabel.VALIDITY_DRAFT.format(language, quote.getValidityDays()) :
            QuoteLabel.VALIDITY.format(language, quote.getValidityDays(), date(quote.getValidUntil())));
        if (quote.getDepositPercent() != null) {
            BigDecimal deposit = total.multiply(BigDecimal.valueOf(quote.getDepositPercent())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            mentions.add(QuoteLabel.DEPOSIT.format(language, quote.getDepositPercent(), euros(deposit)));
        }
        if (paymentTermDays != null) {
            mentions.add(QuoteLabel.PAYMENT.format(language, paymentTermDays));
        }
        mentions.add(InvoiceLabel.penalties(language, seller.getLatePaymentRate()));
        mentions.add(QuoteLabel.FREE.in(language));
        if (terms != null || termsFileAttached) {
            mentions.add(QuoteLabel.TERMS_ATTACHED.in(language));
        }
        return mentions;
    }

    /** The box the client signs in, with the words it writes first. */
    private void acceptance() throws IOException {
        writer.ensureSpace(LINE * 4 + SIGNATURE_BOX);
        writer.moveDown(LINE * 2);
        float left = writer.left() + writer.width() / 2;
        writer.paragraph(QuoteLabel.ACCEPTANCE.in(language), true, SMALL, writer.width());
        writer.moveDown(SIGNATURE_BOX + 6);
        writer.box(left, writer.cursor(), writer.right() - left, SIGNATURE_BOX);
    }
}
