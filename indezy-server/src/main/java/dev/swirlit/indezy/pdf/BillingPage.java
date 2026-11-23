package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.BodyBlock;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DocumentTheme;
import dev.swirlit.indezy.model.enums.LogoPlacement;
import dev.swirlit.indezy.model.enums.LogoSize;
import dev.swirlit.indezy.service.InvoiceTotals;
import dev.swirlit.indezy.util.BillingText;

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
    /** The seller's logo, printed above it; null for none. */
    private byte[] logo;
    /** Whether the logo is already drawn, in a modern band. */
    private boolean logoDrawn;
    /** A modern band's height, and its logo's. */
    private static final float BAND = 72;
    private static final float BAND_LOGO = 44;
    private static final String MODERN_DEFAULT = "#1F2937";

    BillingPage(PdfPageWriter writer, InvoiceSeller seller, InvoiceBuyer buyer, DocumentLanguage language) {
        this.writer = writer;
        this.seller = seller;
        this.buyer = buyer;
        this.language = language;
        this.locale = language.locale();
    }

    /** Prints this logo above the seller. */
    void logo(byte[] image) {
        this.logo = image == null ? null : image.clone();
    }

    /** Amounts owed back, as a credit note's, are written negative. */
    boolean negative() {
        return false;
    }

    // What the document says, the same in the PDF and the Word file.

    /** The document's heading ("Facture", "Devis"…). */
    abstract String heading();

    /** The lines under the heading: number, dates, references. */
    abstract List<String> headingLines();

    /** What a draft says it is not; null once issued. */
    abstract String draftMark();

    /** The words before the client ("Facturé à", "Pour"). */
    abstract String buyerLabel();

    abstract List<InvoiceLine> documentLines();

    /** The label of the amount to pay. */
    abstract String totalLabel();

    /** The legal mentions under the totals. */
    abstract List<String> documentMentions();

    /** The seller's notes; null for none. */
    abstract String notes();

    /** The document's own fields for a Word template, by name ("number", "issueDate"…), formatted; absent ones left out. */
    abstract java.util.Map<String, String> fields();

    /** The words above the client's signature box; null without one. */
    String acceptance() {
        return null;
    }

    /** Terms and conditions printed after the document: their title and text. */
    record Terms(String title, String text) {
    }

    /** The terms and conditions printed after the document; null for none. */
    Terms termsPage() {
        return null;
    }

    /** The seller's name as printed, "EI" after it for an individual business. */
    String sellerName() {
        return seller.getName() + (seller.getLegalForm() != null && seller.getLegalForm().isIndividual() ? InvoiceLabel.INDIVIDUAL.in(language) : "");
    }

    /** The column headings of the lines. */
    String[] columnHeadings() {
        return new String[] {InvoiceLabel.COLUMN_DESCRIPTION.in(language), InvoiceLabel.COLUMN_QUANTITY.in(language),
            InvoiceLabel.COLUMN_PRICE.in(language), InvoiceLabel.COLUMN_VAT.in(language), InvoiceLabel.COLUMN_AMOUNT.in(language)};
    }

    /** A line's cells: description, quantity, unit price, VAT rate and amount. */
    String[] cells(InvoiceLine line) {
        return new String[] {line.getDescription(), number(line.getQuantity()), money(line.getUnitPrice()), number(line.getVatRate()) + " %",
            money(InvoiceTotals.lineAmount(line))};
    }

    /** The totals as label and amount: before VAT, of VAT (unless exempt) and to pay, the last one strong. */
    List<String[]> totalRows() {
        InvoiceTotals totals = InvoiceTotals.of(documentLines());
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {InvoiceLabel.TOTAL_EXCLUDING.in(language), signed(totals.excludingVat())});
        if (!vatExempt()) {
            rows.add(new String[] {InvoiceLabel.TOTAL_VAT.in(language), signed(totals.vat())});
        }
        rows.add(new String[] {totalLabel(), signed(totals.total())});
        return rows;
    }

    /** The layout the seller chose; simple when none. */
    DocumentTheme theme() {
        return seller.getTheme() == null ? DocumentTheme.SIMPLE : seller.getTheme();
    }

    /** The colour of a modern band and of what it fills: the accent, or a dark slate without one. */
    private String bandColor() {
        return accent() != null ? accent() : MODERN_DEFAULT;
    }

    /** Lays the document out on the PDF's pages. */
    void write() throws IOException {
        if (theme() == DocumentTheme.MODERN) {
            band();
            header(() -> headingBlock(null, headingLines(), draftMark()), buyerLabel());
        } else {
            writer.footerRule(theme() == DocumentTheme.CLASSIC);
            header(() -> headingBlock(heading(), headingLines(), draftMark()), buyerLabel());
        }
        if (theme() == DocumentTheme.CLASSIC) {
            writer.line(writer.left(), writer.cursor() + 4, writer.right(), writer.cursor() + 4);
        }
        BodyBlock previous = null;
        for (BodyBlock block : blocks()) {
            switch (block) {
                case LINES -> {
                    lines(documentLines());
                    totals();
                }
                case MENTIONS -> mentions(documentMentions());
                case NOTES -> notes(notes(), previous == BodyBlock.MENTIONS);
                default -> signature(); // SIGNATURE
            }
            previous = block;
        }
    }

    /** The body's blocks in the seller's order, a quote's signature box only when kept. */
    List<BodyBlock> blocks() {
        return BodyBlock.read(seller.getBodyBlocks());
    }

    /** A quote's signature box; an invoice has none. */
    void signature() throws IOException {
        // Only a quote is signed.
    }

    /** A modern document's band across the top: the logo on its left and the title in white on its right. */
    private void band() throws IOException {
        float top = writer.pageHeight();
        writer.fill(0, top - BAND, writer.pageWidth(), BAND, bandColor());
        if (logo != null) {
            writer.image(logo, writer.left(), top - (BAND - BAND_LOGO) / 2, BAND_LOGO * 3, BAND_LOGO);
        }
        writer.white();
        writer.textRight(writer.right(), top - BAND / 2 - TITLE / 3, heading(), true, TITLE);
        writer.color(null);
        writer.moveDown(writer.cursor() - (top - BAND - LINE));
        logoDrawn = true;
    }

    void sellerBlock() throws IOException {
        block(sellerName(), sellerLines(), writer.width() / 2);
    }

    /** The seller's lines under its name: the header line, address, legal form and capital, SIRET and VAT number. */
    List<String> sellerLines() {
        List<String> lines = new ArrayList<>();
        if (seller.getHeader() != null && !seller.getHeader().isBlank()) {
            lines.add(seller.getHeader().strip());
        }
        lines.addAll(splitLines(seller.getAddress()));
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
        return lines;
    }

    /** The document's title, right-aligned, then its lines below it; without a title (in a modern band), its lines only. */
    void headingBlock(String heading, List<String> lines, String draftMark) throws IOException {
        float right = writer.right();
        if (heading != null) {
            writer.moveDown(TITLE);
            writer.color(accent());
            writer.textRight(right, writer.cursor(), heading, true, TITLE);
            writer.color(null);
        }
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
        block(buyer.getName(), buyerLines(), writer.width() / 2);
    }

    /** The client's lines under its name: address, SIREN and VAT number. */
    List<String> buyerLines() {
        List<String> lines = new ArrayList<>(splitLines(buyer.getAddress()));
        if (buyer.getSiren() != null) {
            lines.add(InvoiceLabel.SIREN.format(language, buyer.getSiren()));
        }
        if (buyer.getVatNumber() != null) {
            lines.add(InvoiceLabel.VAT_NUMBER.format(language, buyer.getVatNumber()));
        }
        return lines;
    }

    /** The seller beside the document's heading, then the client below both. */
    void header(HeadingWriter heading, String buyerLabel) throws IOException {
        float top = writer.cursor();
        if (logo != null && !logoDrawn) {
            LogoSize size = seller.getLogoSize() == null ? LogoSize.MEDIUM : seller.getLogoSize();
            LogoPlacement placement = theme() == DocumentTheme.CLASSIC ? LogoPlacement.CENTER :
                seller.getLogoPlacement() == null ? LogoPlacement.LEFT : seller.getLogoPlacement();
            float x = switch (placement) {
                case LEFT -> writer.left();
                case CENTER -> writer.left() + (writer.width() - writer.imageWidth(logo, size.width(), size.height())) / 2;
                case RIGHT -> writer.right() - writer.imageWidth(logo, size.width(), size.height());
            };
            float height = writer.image(logo, x, top, size.width(), size.height()) + LINE;
            writer.moveDown(height);
            if (placement != LogoPlacement.LEFT) {
                // Centred or on the right, the logo sits above both the seller and the title.
                top -= height;
            }
        }
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
        if (theme() == DocumentTheme.MODERN) {
            writer.fill(writer.left() - 4, writer.cursor() - 6, writer.width() + 8, ROW, bandColor());
            writer.white();
            row(columns, columnHeadings(), true);
        } else {
            writer.color(accent());
            row(columns, columnHeadings(), true);
            writer.line(writer.left(), writer.cursor() - 5, writer.right(), writer.cursor() - 5);
        }
        writer.color(null);
        int index = 0;
        for (InvoiceLine line : lines) {
            List<String> description = writer.wrap(line.getDescription(), false, BODY, writer.width() * (columns[1] - 0.02f));
            writer.ensureSpace(ROW + LINE * (description.size() - 1));
            writer.moveDown(ROW);
            if (theme() == DocumentTheme.CLASSIC && index++ % 2 != 0) {
                writer.shade(writer.left() - 4, writer.cursor() - 5 - LINE * (description.size() - 1), writer.width() + 8,
                    ROW + LINE * (description.size() - 1) - 1);
            }
            String[] cells = cells(line);
            cells[0] = description.getFirst();
            row(columns, cells, false);
            for (String more : description.subList(1, description.size())) {
                writer.moveDown(LINE);
                writer.text(writer.left(), writer.cursor(), more, false, BODY);
            }
        }
        writer.color(accent());
        writer.line(writer.left(), writer.cursor() - 6, writer.right(), writer.cursor() - 6);
        writer.color(null);
    }

    /** The seller's accent colour, when a valid one. */
    String accent() {
        String color = seller.getAccentColor();
        return color != null && color.matches("#[0-9A-Fa-f]{6}") ? color : null;
    }

    /** The totals before VAT, of VAT (unless exempt) and to pay, under the lines. */
    void totals() throws IOException {
        writer.ensureSpace(ROW * 4);
        float labelX = writer.left() + writer.width() * 0.6f;
        List<String[]> rows = totalRows();
        for (int index = 0; index < rows.size(); index++) {
            boolean last = index == rows.size() - 1;
            if (last && theme() == DocumentTheme.MODERN) {
                writer.fill(labelX - 8, writer.cursor() - ROW - 6, writer.right() - labelX + 12, ROW + 4, bandColor());
                writer.white();
                total(labelX, rows.get(index)[0], rows.get(index)[1], true);
                writer.color(null);
            } else {
                total(labelX, rows.get(index)[0], rows.get(index)[1], last);
            }
        }
    }

    boolean vatExempt() {
        return Boolean.TRUE.equals(seller.getVatExempt());
    }

    /** The mentions in small letters. */
    void mentions(List<String> mentions) throws IOException {
        writer.moveDown(LINE * 2);
        String bank = seller.getIban() == null ? null : BillingText.groupIban(seller.getIban());
        for (String mention : mentions) {
            writer.ensureSpace(LINE * 2);
            float before = writer.cursor();
            boolean framed = theme() == DocumentTheme.CLASSIC && bank != null && mention.contains(bank);
            if (framed) {
                writer.moveDown(4);
            }
            writer.paragraph(mention, false, SMALL, writer.width());
            if (framed) {
                writer.box(writer.left() - 4, writer.cursor() - 4, writer.width() + 8, before - writer.cursor() + 2);
                writer.moveDown(4);
            }
            writer.moveDown(3);
        }
    }

    /** The notes under their title, right after the mentions or further down after another block. */
    void notes(String notes, boolean afterMentions) throws IOException {
        if (notes != null) {
            writer.ensureSpace(LINE * 3);
            writer.moveDown(afterMentions ? LINE : LINE * 2);
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

    String number(BigDecimal value) {
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
