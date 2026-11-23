package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.InvoiceCurrency;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.service.InvoiceTotals;
import dev.swirlit.indezy.util.BillingText;

import java.math.BigDecimal;
import java.text.NumberFormat;
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

    @Override
    String heading() {
        InvoiceLabel title = invoice.isCreditNote() ? InvoiceLabel.CREDIT_TITLE : invoice.isDeposit() ? InvoiceLabel.DEPOSIT_TITLE : InvoiceLabel.TITLE;
        return title.in(language);
    }

    @Override
    boolean negative() {
        return invoice.isCreditNote();
    }

    @Override
    String currencyCode() {
        return invoice.getCurrency().name();
    }

    private String signedEuros(BigDecimal amount) {
        return euros(negative() ? amount.negate() : amount);
    }

    @Override
    String buyerLabel() {
        return InvoiceLabel.BILL_TO.in(language);
    }

    @Override
    List<InvoiceLine> documentLines() {
        return invoice.getLines();
    }

    @Override
    String totalLabel() {
        return (vatExempt() && !invoice.isCreditNote() ? InvoiceLabel.TOTAL_EXEMPT : InvoiceLabel.TOTAL).in(language);
    }

    @Override
    String notes() {
        return invoice.getNotes();
    }

    @Override
    String draftMark() {
        return invoice.getStatus() != InvoiceStatus.DRAFT ? null : (invoice.isCreditNote() ? InvoiceLabel.CREDIT_DRAFT : InvoiceLabel.DRAFT).in(language);
    }

    @Override
    List<String> headingLines() {
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
        return lines;
    }

    @Override
    List<String> documentMentions() {
        List<String> mentions = new ArrayList<>();
        if (vatExempt()) {
            mentions.add(InvoiceLabel.VAT_EXEMPT.in(language));
        }
        InvoiceTotals euros = invoice.getCurrency() == InvoiceCurrency.EUR ? null : InvoiceTotals.inEuros(invoice);
        if (euros != null) {
            // In another currency, the rate and the amounts in euros, the VAT's above all (art. 242 nonies A, annexe II du CGI).
            NumberFormat rate = NumberFormat.getNumberInstance(language.locale());
            rate.setMaximumFractionDigits(6);
            mentions.add(InvoiceLabel.EXCHANGE_RATE.format(language, invoice.getCurrency().name(), rate.format(invoice.getExchangeRate())));
            mentions.add(vatExempt() ? InvoiceLabel.IN_EUROS_EXEMPT.format(language, signedEuros(euros.total())) :
                InvoiceLabel.IN_EUROS.format(language, signedEuros(euros.excludingVat()), signedEuros(euros.vat()), signedEuros(euros.total())));
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
            mentions.add(InvoiceLabel.penalties(language, seller.getLatePaymentRate()));
        }
        return mentions;
    }
}
