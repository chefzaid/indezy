package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
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

    @Override
    void write() throws IOException {
        super.write();
        signatureBox();
        if (terms != null) {
            writer.startPage();
            writer.paragraph(QuoteLabel.TERMS_TITLE.in(language), true, TERMS_TITLE_SIZE, writer.width());
            writer.moveDown(LINE);
            writer.paragraph(terms, false, SMALL, writer.width());
        }
    }

    @Override
    String heading() {
        return QuoteLabel.TITLE.in(language);
    }

    @Override
    java.util.Map<String, String> fields() {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("title", heading());
        if (quote.getNumber() != null) {
            fields.put("number", quote.getNumber());
        }
        if (quote.getIssueDate() != null) {
            fields.put("issueDate", date(quote.getIssueDate()));
        }
        if (quote.getValidUntil() != null) {
            fields.put("validUntil", date(quote.getValidUntil()));
        }
        if (draftMark() != null) {
            fields.put("draft", draftMark());
        }
        return fields;
    }

    @Override
    String buyerLabel() {
        return QuoteLabel.FOR.in(language);
    }

    @Override
    List<InvoiceLine> documentLines() {
        return quote.getLines();
    }

    @Override
    String totalLabel() {
        return (vatExempt() ? InvoiceLabel.TOTAL_EXEMPT : InvoiceLabel.TOTAL).in(language);
    }

    @Override
    String notes() {
        return quote.getNotes();
    }

    @Override
    String draftMark() {
        return quote.getStatus() == QuoteStatus.DRAFT ? QuoteLabel.DRAFT.in(language) : null;
    }

    @Override
    String acceptance() {
        return QuoteLabel.ACCEPTANCE.in(language);
    }

    @Override
    Terms termsPage() {
        return terms == null ? null : new Terms(QuoteLabel.TERMS_TITLE.in(language), terms);
    }

    @Override
    List<String> headingLines() {
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
        return lines;
    }

    @Override
    List<String> documentMentions() {
        BigDecimal total = InvoiceTotals.of(quote.getLines()).total();
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
    private void signatureBox() throws IOException {
        writer.ensureSpace(LINE * 4 + SIGNATURE_BOX);
        writer.moveDown(LINE * 2);
        float left = writer.left() + writer.width() / 2;
        writer.paragraph(acceptance(), true, SMALL, writer.width());
        writer.moveDown(SIGNATURE_BOX + 6);
        writer.box(left, writer.cursor(), writer.right() - left, SIGNATURE_BOX);
    }
}
