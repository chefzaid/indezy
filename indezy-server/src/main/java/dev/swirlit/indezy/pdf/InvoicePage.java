package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.service.InvoiceTotals;
import dev.swirlit.indezy.util.BillingText;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays out an invoice: the seller and the invoice's number and dates, the client, the lines, the totals, then the
 * mentions French law requires (VAT exemption, payment term and bank details, no discount, late penalties and the €40
 * recovery fee). A draft says, in red letters, that it is not an invoice.
 */
final class InvoicePage extends BillingPage {

    private final Invoice invoice;

    InvoicePage(PdfPageWriter writer, Invoice invoice, InvoiceSeller seller, InvoiceBuyer buyer) {
        super(writer, seller, buyer, invoice.getLanguage());
        this.invoice = invoice;
    }

    String title() {
        return heading() + (invoice.getNumber() == null ? "" : " " + invoice.getNumber());
    }

    private String heading() {
        InvoiceLabel title = invoice.isCreditNote() ? InvoiceLabel.CREDIT_TITLE : invoice.isDeposit() ? InvoiceLabel.DEPOSIT_TITLE : InvoiceLabel.TITLE;
        return title.in(language);
    }

    @Override
    boolean negative() {
        return invoice.isCreditNote();
    }

    void write() throws IOException {
        header(this::invoiceBlock, InvoiceLabel.BILL_TO.in(language));
        lines(invoice.getLines());
        totals(InvoiceTotals.of(invoice.getLines()),
            (vatExempt() && !invoice.isCreditNote() ? InvoiceLabel.TOTAL_EXEMPT : InvoiceLabel.TOTAL).in(language));
        mentions(mentions(), invoice.getNotes());
    }

    private void invoiceBlock() throws IOException {
        List<String> lines = new ArrayList<>();
        if (invoice.getNumber() != null) {
            lines.add(InvoiceLabel.NUMBER.format(language, invoice.getNumber()));
        }
        if (invoice.getIssueDate() != null) {
            lines.add(InvoiceLabel.ISSUED.format(language, date(invoice.getIssueDate())));
            if (invoice.getDueDate() != null) {
                lines.add(InvoiceLabel.DUE.format(language, date(invoice.getDueDate())));
            }
        }
        if (invoice.isCreditNote() && invoice.getCreditedNumber() != null) {
            lines.add(InvoiceLabel.CREDITS.format(language, invoice.getCreditedNumber(), date(invoice.getCreditedIssueDate())));
        }
        if (invoice.getQuoteNumber() != null) {
            lines.add(InvoiceLabel.QUOTE.format(language, invoice.getQuoteNumber()));
        }
        if (invoice.getPeriodStart() != null && invoice.getPeriodEnd() != null) {
            lines.add(InvoiceLabel.PERIOD.format(language, date(invoice.getPeriodStart()), date(invoice.getPeriodEnd())));
        }
        String draft = invoice.getStatus() != InvoiceStatus.DRAFT ? null :
            (invoice.isCreditNote() ? InvoiceLabel.CREDIT_DRAFT : InvoiceLabel.DRAFT).in(language);
        headingBlock(heading(), lines, draft);
    }

    private List<String> mentions() {
        List<String> mentions = new ArrayList<>();
        if (vatExempt()) {
            mentions.add(InvoiceLabel.VAT_EXEMPT.in(language));
        }
        if (!invoice.isCreditNote() && invoice.getIssueDate() != null && invoice.getDueDate() != null) {
            mentions.add(InvoiceLabel.PAYMENT.format(language, ChronoUnit.DAYS.between(invoice.getIssueDate(), invoice.getDueDate()),
                date(invoice.getDueDate())));
        }
        if (seller.getIban() != null && !invoice.isCreditNote()) {
            mentions.add(InvoiceLabel.BANK.format(language, BillingText.groupIban(seller.getIban())) +
                (seller.getBic() == null ? "" : "   " + InvoiceLabel.BIC.format(language, seller.getBic())));
        }
        if (!invoice.isCreditNote()) {
            mentions.add(InvoiceLabel.DISCOUNT.in(language));
            mentions.add(InvoiceLabel.PENALTIES.in(language));
        }
        return mentions;
    }
}
