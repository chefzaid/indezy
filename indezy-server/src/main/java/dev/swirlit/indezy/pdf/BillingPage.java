package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.service.InvoiceTotals;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * What an invoice and a quote lay out alike: the seller with its legal mentions, the client, the lines and the totals,
 * in the document's language.
 */
abstract class BillingPage {

    static final float TITLE = 20;
    static final float BODY = 9.5f;
    static final float SMALL = 8;
    static final float LINE = 13;
    static final float ROW = 16;

    final PdfPageWriter writer;
    final InvoiceSeller seller;
    final InvoiceBuyer buyer;
    final DocumentLanguage language;
    private final Locale locale;

    BillingPage(PdfPageWriter writer, InvoiceSeller seller, InvoiceBuyer buyer, DocumentLanguage language) {
        this.writer = writer;
        this.seller = seller;
        this.buyer = buyer;
        this.language = language;
        this.locale = language.locale();
    }

    /** Amounts owed back, as a credit note's, are written negative. */
    boolean negative() {
        return false;
    }

    void sellerBlock() throws IOException {
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

    /** The document's title, right-aligned, then its lines below it. */
    void headingBlock(String heading, List<String> lines, String draftMark) throws IOException {
        float right = writer.right();
        writer.moveDown(TITLE);
        writer.textRight(right, writer.cursor(), heading, true, TITLE);
        for (String line : lines) {
            writer.moveDown(LINE);
            writer.textRight(right, writer.cursor(), line, false, BODY);
        }
        if (draftMark != null) {
            writer.moveDown(LINE);
            writer.textRight(right, writer.cursor(), draftMark, true, BODY);
        }
    }

    void buyerBlock(String label) throws IOException {
        writer.text(writer.left(), writer.cursor(), label, false, SMALL);
        List<String> lines = new ArrayList<>(splitLines(buyer.getAddress()));
        if (buyer.getSiren() != null) {
            lines.add(InvoiceLabel.SIREN.format(language, buyer.getSiren()));
        }
        if (buyer.getVatNumber() != null) {
            lines.add(InvoiceLabel.VAT_NUMBER.format(language, buyer.getVatNumber()));
        }
        block(buyer.getName(), lines, writer.width() / 2);
    }

    /** The seller beside the document's heading, then the client below both. */
    void header(HeadingWriter heading, String buyerLabel) throws IOException {
        float top = writer.cursor();
        sellerBlock();
        float afterSeller = writer.cursor();
        moveTo(top);
        heading.write();
        moveTo(Math.min(afterSeller, writer.cursor()));
        writer.moveDown(LINE * 1.5f);
        buyerBlock(buyerLabel);
        writer.moveDown(LINE);
    }

    void lines(List<InvoiceLine> lines) throws IOException {
        float[] columns = {0, 0.56f, 0.66f, 0.8f, 0.88f};
        writer.moveDown(ROW);
        row(columns, new String[] {InvoiceLabel.COLUMN_DESCRIPTION.in(language), InvoiceLabel.COLUMN_QUANTITY.in(language),
            InvoiceLabel.COLUMN_PRICE.in(language), InvoiceLabel.COLUMN_VAT.in(language), InvoiceLabel.COLUMN_AMOUNT.in(language)}, true);
        writer.line(writer.left(), writer.cursor() - 5, writer.right(), writer.cursor() - 5);
        for (InvoiceLine line : lines) {
            List<String> description = writer.wrap(line.getDescription(), false, BODY, writer.width() * (columns[1] - 0.02f));
            writer.ensureSpace(ROW + LINE * (description.size() - 1));
            writer.moveDown(ROW);
            row(columns, new String[] {description.getFirst(), number(line.getQuantity()), money(line.getUnitPrice()),
                number(line.getVatRate()) + " %", money(InvoiceTotals.lineAmount(line))}, false);
            for (String more : description.subList(1, description.size())) {
                writer.moveDown(LINE);
                writer.text(writer.left(), writer.cursor(), more, false, BODY);
            }
        }
        writer.line(writer.left(), writer.cursor() - 6, writer.right(), writer.cursor() - 6);
    }

    /** The totals before VAT, of VAT (unless exempt) and to pay, under the lines. */
    void totals(InvoiceTotals totals, String totalLabel) throws IOException {
        writer.ensureSpace(ROW * 4);
        float labelX = writer.left() + writer.width() * 0.6f;
        total(labelX, InvoiceLabel.TOTAL_EXCLUDING.in(language), signed(totals.excludingVat()), false);
        if (!vatExempt()) {
            total(labelX, InvoiceLabel.TOTAL_VAT.in(language), signed(totals.vat()), false);
        }
        total(labelX, totalLabel, signed(totals.total()), true);
    }

    boolean vatExempt() {
        return Boolean.TRUE.equals(seller.getVatExempt());
    }

    /** The mentions in small letters, then the notes. */
    void mentions(List<String> mentions, String notes) throws IOException {
        writer.moveDown(LINE * 2);
        for (String mention : mentions) {
            writer.ensureSpace(LINE * 2);
            writer.paragraph(mention, false, SMALL, writer.width());
            writer.moveDown(3);
        }
        if (notes != null) {
            writer.ensureSpace(LINE * 3);
            writer.moveDown(LINE);
            writer.text(writer.left(), writer.cursor(), InvoiceLabel.NOTES.in(language), true, SMALL);
            writer.moveDown(4);
            writer.paragraph(notes, false, SMALL, writer.width());
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

    String date(LocalDate date) {
        return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale));
    }

    String euros(BigDecimal amount) {
        return amount(amount, "EUR");
    }

    /** The currency of the lines and totals: the euro, unless the document is written in another. */
    String currencyCode() {
        return "EUR";
    }

    /** An amount of the document's currency. */
    String money(BigDecimal amount) {
        return amount(amount, currencyCode());
    }

    private String amount(BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(Currency.getInstance(currency));
        return plainSpaces(format.format(amount));
    }

    String signed(BigDecimal amount) {
        return money(negative() ? amount.negate() : amount);
    }

    private String number(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return plainSpaces(format.format(value));
    }

    /** The fonts draw no narrow no-break space: French groups digits with a plain one. */
    private static String plainSpaces(String text) {
        return text.replace(' ', ' ').replace(' ', ' ');
    }

    /** Writes the document's heading block, beside the seller. */
    @FunctionalInterface
    interface HeadingWriter {
        void write() throws IOException;
    }
}
