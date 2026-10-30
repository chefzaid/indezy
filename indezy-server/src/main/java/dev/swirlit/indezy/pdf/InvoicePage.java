package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.service.InvoiceTotals;
import dev.swirlit.indezy.util.BillingText;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * Lays out an invoice: the seller and the invoice's number and dates, the client, the lines, the totals, then the
 * mentions French law requires (VAT exemption, payment term and bank details, no discount, late penalties and the €40
 * recovery fee). A draft says, in red letters, that it is not an invoice.
 */
final class InvoicePage {

    private static final float TITLE = 20;
    private static final float BODY = 9.5f;
    private static final float SMALL = 8;
    private static final float LINE = 13;
    private static final float ROW = 16;

    private final PdfPageWriter writer;
    private final Invoice invoice;
    private final InvoiceSeller seller;
    private final InvoiceBuyer buyer;
    private final DocumentLanguage language;
    private final Locale locale;

    InvoicePage(PdfPageWriter writer, Invoice invoice, InvoiceSeller seller, InvoiceBuyer buyer) {
        this.writer = writer;
        this.invoice = invoice;
        this.seller = seller;
        this.buyer = buyer;
        this.language = invoice.getLanguage();
        this.locale = language.locale();
    }

    String title() {
        return heading() + (invoice.getNumber() == null ? "" : " " + invoice.getNumber());
    }

    private String heading() {
        return (invoice.isCreditNote() ? InvoiceLabel.CREDIT_TITLE : InvoiceLabel.TITLE).in(language);
    }

    void write() throws IOException {
        float top = writer.cursor();
        sellerBlock();
        float afterSeller = writer.cursor();
        moveTo(top);
        invoiceBlock();
        moveTo(Math.min(afterSeller, writer.cursor()));
        writer.moveDown(LINE * 1.5f);
        buyerBlock();
        writer.moveDown(LINE);
        lines();
        totals();
        mentions();
    }

    private void sellerBlock() throws IOException {
        String name = seller.getName() + (seller.getLegalForm() != null && seller.getLegalForm().isIndividual() ?
            InvoiceLabel.INDIVIDUAL.in(language) : "");
        List<String> lines = new ArrayList<>(splitLines(seller.getAddress()));
        if (seller.getLegalForm() != null && seller.getLegalForm().isCompany()) {
            lines.add(seller.getShareCapital() == null ? seller.getLegalForm().name() :
                InvoiceLabel.CAPITAL.format(language, seller.getLegalForm().name(), euros(BigDecimal.valueOf(seller.getShareCapital()))));
            if (seller.getRegistration() != null) {
                lines.add(seller.getRegistration());
            }
        }
        if (seller.getSiret() != null) {
            lines.add(InvoiceLabel.SIRET.format(language, seller.getSiret()));
        }
        if (seller.getVatNumber() != null && !Boolean.TRUE.equals(seller.getVatExempt())) {
            lines.add(InvoiceLabel.VAT_NUMBER.format(language, seller.getVatNumber()));
        }
        block(name, lines, writer.width() / 2);
    }

    private void invoiceBlock() throws IOException {
        float right = writer.right();
        writer.moveDown(TITLE);
        writer.textRight(right, writer.cursor(), heading(), true, TITLE);
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
        if (invoice.getPeriodStart() != null && invoice.getPeriodEnd() != null) {
            lines.add(InvoiceLabel.PERIOD.format(language, date(invoice.getPeriodStart()), date(invoice.getPeriodEnd())));
        }
        for (String line : lines) {
            writer.moveDown(LINE);
            writer.textRight(right, writer.cursor(), line, false, BODY);
        }
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            writer.moveDown(LINE);
            writer.textRight(right, writer.cursor(), (invoice.isCreditNote() ? InvoiceLabel.CREDIT_DRAFT : InvoiceLabel.DRAFT).in(language),
                true, BODY);
        }
    }

    private void buyerBlock() throws IOException {
        writer.text(writer.left(), writer.cursor(), InvoiceLabel.BILL_TO.in(language), false, SMALL);
        List<String> lines = new ArrayList<>(splitLines(buyer.getAddress()));
        if (buyer.getSiren() != null) {
            lines.add(InvoiceLabel.SIREN.format(language, buyer.getSiren()));
        }
        if (buyer.getVatNumber() != null) {
            lines.add(InvoiceLabel.VAT_NUMBER.format(language, buyer.getVatNumber()));
        }
        block(buyer.getName(), lines, writer.width() / 2);
    }

    private void lines() throws IOException {
        float[] columns = {0, 0.56f, 0.66f, 0.8f, 0.88f};
        writer.moveDown(ROW);
        row(columns, new String[] {InvoiceLabel.COLUMN_DESCRIPTION.in(language), InvoiceLabel.COLUMN_QUANTITY.in(language),
            InvoiceLabel.COLUMN_PRICE.in(language), InvoiceLabel.COLUMN_VAT.in(language), InvoiceLabel.COLUMN_AMOUNT.in(language)}, true);
        writer.line(writer.left(), writer.cursor() - 5, writer.right(), writer.cursor() - 5);
        for (InvoiceLine line : invoice.getLines()) {
            List<String> description = writer.wrap(line.getDescription(), false, BODY, writer.width() * (columns[1] - 0.02f));
            writer.ensureSpace(ROW + LINE * (description.size() - 1));
            writer.moveDown(ROW);
            row(columns, new String[] {description.getFirst(), number(line.getQuantity()), euros(line.getUnitPrice()),
                number(line.getVatRate()) + " %", euros(InvoiceTotals.lineAmount(line))}, false);
            for (String more : description.subList(1, description.size())) {
                writer.moveDown(LINE);
                writer.text(writer.left(), writer.cursor(), more, false, BODY);
            }
        }
        writer.line(writer.left(), writer.cursor() - 6, writer.right(), writer.cursor() - 6);
    }

    private void totals() throws IOException {
        InvoiceTotals totals = InvoiceTotals.of(invoice.getLines());
        boolean exempt = Boolean.TRUE.equals(seller.getVatExempt());
        writer.ensureSpace(ROW * 4);
        float labelX = writer.left() + writer.width() * 0.6f;
        total(labelX, InvoiceLabel.TOTAL_EXCLUDING.in(language), signed(totals.excludingVat()), false);
        if (!exempt) {
            total(labelX, InvoiceLabel.TOTAL_VAT.in(language), signed(totals.vat()), false);
        }
        total(labelX, (exempt && !invoice.isCreditNote() ? InvoiceLabel.TOTAL_EXEMPT : InvoiceLabel.TOTAL).in(language),
            signed(totals.total()), true);
    }

    private void mentions() throws IOException {
        List<String> mentions = new ArrayList<>();
        if (Boolean.TRUE.equals(seller.getVatExempt())) {
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
        writer.moveDown(LINE * 2);
        for (String mention : mentions) {
            writer.ensureSpace(LINE * 2);
            writer.paragraph(mention, false, SMALL, writer.width());
            writer.moveDown(3);
        }
        if (invoice.getNotes() != null) {
            writer.ensureSpace(LINE * 3);
            writer.moveDown(LINE);
            writer.text(writer.left(), writer.cursor(), InvoiceLabel.NOTES.in(language), true, SMALL);
            writer.moveDown(4);
            writer.paragraph(invoice.getNotes(), false, SMALL, writer.width());
        }
    }

    private void block(String title, List<String> lines, float width) throws IOException {
        writer.moveDown(LINE);
        writer.text(writer.left(), writer.cursor(), writer.ellipsize(title == null ? "" : title, true, 11, width), true, 11);
        for (String line : lines) {
            writer.moveDown(LINE);
            writer.text(writer.left(), writer.cursor(), writer.ellipsize(line, false, BODY, width), false, BODY);
        }
    }

    private void total(float labelX, String label, String amount, boolean strong) throws IOException {
        writer.moveDown(ROW);
        writer.text(labelX, writer.cursor(), label, strong, strong ? 11 : BODY);
        writer.textRight(writer.right(), writer.cursor(), amount, strong, strong ? 11 : BODY);
    }

    private void row(float[] columns, String[] cells, boolean bold) throws IOException {
        for (int index = 0; index < cells.length; index++) {
            float x = writer.left() + writer.width() * columns[index];
            if (index == 0) {
                writer.text(x, writer.cursor(), cells[index], bold, BODY);
            } else {
                float end = index + 1 < columns.length ? writer.left() + writer.width() * columns[index + 1] - 6 : writer.right();
                writer.textRight(end, writer.cursor(), cells[index], bold, BODY);
            }
        }
    }

    private void moveTo(float cursor) {
        writer.moveDown(writer.cursor() - cursor);
    }

    private static List<String> splitLines(String text) {
        return text == null ? List.of() : text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    private String date(LocalDate date) {
        return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale));
    }

    private String euros(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(Currency.getInstance("EUR"));
        return plainSpaces(format.format(amount));
    }

    /** A credit note's totals are owed back: written negative. */
    private String signed(BigDecimal amount) {
        return euros(invoice.isCreditNote() ? amount.negate() : amount);
    }

    private String number(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return plainSpaces(format.format(value));
    }

    /** The fonts draw no narrow no-break space: French groups digits with a plain one. */
    private static String plainSpaces(String text) {
        return text.replace('\u202F', ' ').replace('\u00A0', ' ');
    }
}
