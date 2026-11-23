package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.enums.BodyBlock;
import dev.swirlit.indezy.model.enums.LogoPlacement;
import dev.swirlit.indezy.model.enums.LogoSize;

import java.util.ArrayList;
import java.util.List;

/**
 * An invoice or a quote as a Word file, laid out like its PDF from the same {@link BillingPage}: the logo, the seller
 * beside the heading, the client, the lines, the totals, the mentions and notes, a quote's signature box and its terms.
 */
final class BillingDocx {

    private static final int[] HEADER = {DocxDocument.CONTENT_WIDTH / 2, DocxDocument.CONTENT_WIDTH - DocxDocument.CONTENT_WIDTH / 2};
    private static final int[] LINES = columns(0.56, 0.10, 0.14, 0.08, 0.12);
    private static final int[] TOTALS = columns(0.60, 0.25, 0.15);
    private static final int[] SIGNATURE = columns(0.5, 0.5);
    private static final int SIGNATURE_LINES = 5;

    private BillingDocx() {
    }

    static byte[] render(BillingPage page, byte[] logo, String title) {
        String accent = page.accent() == null ? null : page.accent().substring(1).toUpperCase(java.util.Locale.ROOT);
        DocxDocument docx = new DocxDocument(BillingFonts.family(page.seller), page.zones());
        if (logo != null) {
            LogoSize size = page.seller.getLogoSize() == null ? LogoSize.MEDIUM : page.seller.getLogoSize();
            LogoPlacement placement = page.seller.getLogoPlacement() == null ? LogoPlacement.LEFT : page.seller.getLogoPlacement();
            docx.picture(logo, size.width(), size.height(), switch (placement) {
                case LEFT -> DocxDocument.Align.LEFT;
                case CENTER -> DocxDocument.Align.CENTER;
                case RIGHT -> DocxDocument.Align.RIGHT;
            });
        }
        List<List<DocxDocument.Run>> seller = new ArrayList<>();
        seller.add(List.of(new DocxDocument.Run(page.sellerName(), true, DocxDocument.NAME, null)));
        page.sellerLines().forEach(line -> seller.add(List.of(plain(line))));
        List<List<DocxDocument.Run>> heading = new ArrayList<>();
        heading.add(List.of(new DocxDocument.Run(page.heading(), true, DocxDocument.TITLE, accent)));
        page.headingLines().forEach(line -> heading.add(List.of(plain(line))));
        if (page.draftMark() != null) {
            heading.add(List.of(new DocxDocument.Run(page.draftMark(), true, DocxDocument.BODY, null)));
        }
        docx.table(HEADER, List.of(List.of(new DocxDocument.Cell(seller, DocxDocument.Align.LEFT),
            new DocxDocument.Cell(heading, DocxDocument.Align.RIGHT))), false, false, false, accent);
        docx.paragraph("", false, DocxDocument.BODY, DocxDocument.Align.LEFT);
        docx.paragraph(page.buyerLabel(), false, DocxDocument.SMALL, DocxDocument.Align.LEFT);
        docx.paragraph(List.of(new DocxDocument.Run(page.buyer.getName(), true, DocxDocument.NAME, null)), DocxDocument.Align.LEFT, 20);
        page.buyerLines().forEach(line -> docx.paragraph(List.of(plain(line)), DocxDocument.Align.LEFT, 20));
        docx.paragraph("", false, DocxDocument.BODY, DocxDocument.Align.LEFT);

        for (BodyBlock block : page.blocks()) {
            switch (block) {
                case LINES -> lines(docx, page, accent);
                case MENTIONS -> mentions(docx, page);
                case NOTES -> notes(docx, page);
                default -> signature(docx, page); // SIGNATURE
            }
        }
        BillingPage.Terms terms = page.termsPage();
        if (terms != null) {
            docx.pageBreak();
            docx.paragraph(List.of(new DocxDocument.Run(terms.title(), true, 24, null)), DocxDocument.Align.LEFT, 160);
            for (String paragraph : terms.text().split("\\R\\s*\\R")) {
                docx.paragraph(List.of(new DocxDocument.Run(paragraph.strip(), false, DocxDocument.SMALL, null)), DocxDocument.Align.LEFT, 100);
            }
        }
        return docx.save(title, page.seller.getName() == null ? "" : page.seller.getName());
    }

    /** The lines, then the totals under them. */
    private static void lines(DocxDocument docx, BillingPage page, String accent) {
        List<List<DocxDocument.Cell>> rows = new ArrayList<>();
        rows.add(row(page.columnHeadings(), true, accent));
        for (InvoiceLine line : page.documentLines()) {
            rows.add(row(page.cells(line), false, null));
        }
        docx.table(LINES, rows, true, true, false, accent);

        List<List<DocxDocument.Cell>> totals = new ArrayList<>();
        List<String[]> totalRows = page.totalRows();
        for (int index = 0; index < totalRows.size(); index++) {
            boolean strong = index == totalRows.size() - 1;
            int size = strong ? DocxDocument.NAME : DocxDocument.BODY;
            totals.add(List.of(new DocxDocument.Cell(List.of(), DocxDocument.Align.LEFT),
                new DocxDocument.Cell(List.of(List.of(new DocxDocument.Run(totalRows.get(index)[0], strong, size, null))), DocxDocument.Align.LEFT),
                new DocxDocument.Cell(List.of(List.of(new DocxDocument.Run(totalRows.get(index)[1], strong, size, null))), DocxDocument.Align.RIGHT)));
        }
        docx.table(TOTALS, totals, false, false, false, accent);
    }

    /** The mentions in small letters. */
    private static void mentions(DocxDocument docx, BillingPage page) {
        docx.paragraph("", false, DocxDocument.BODY, DocxDocument.Align.LEFT);
        for (String mention : page.documentMentions()) {
            docx.paragraph(List.of(new DocxDocument.Run(mention, false, DocxDocument.SMALL, null)), DocxDocument.Align.LEFT, 60);
        }
    }

    /** The notes under their title. */
    private static void notes(DocxDocument docx, BillingPage page) {
        if (page.notes() != null) {
            docx.paragraph("", false, DocxDocument.SMALL, DocxDocument.Align.LEFT);
            docx.paragraph(InvoiceLabel.NOTES.in(page.language), true, DocxDocument.SMALL, DocxDocument.Align.LEFT);
            docx.paragraph(List.of(new DocxDocument.Run(page.notes(), false, DocxDocument.SMALL, null)), DocxDocument.Align.LEFT, 60);
        }
    }

    /** A quote's box for the client's signature, with the words written first; none on an invoice. */
    private static void signature(DocxDocument docx, BillingPage page) {
        if (page.acceptance() != null) {
            docx.paragraph("", false, DocxDocument.SMALL, DocxDocument.Align.LEFT);
            docx.paragraph(page.acceptance(), true, DocxDocument.SMALL, DocxDocument.Align.LEFT);
            List<List<DocxDocument.Run>> box = new ArrayList<>();
            for (int line = 0; line < SIGNATURE_LINES; line++) {
                box.add(List.of(plain("")));
            }
            docx.table(SIGNATURE, List.of(List.of(new DocxDocument.Cell(List.of(), DocxDocument.Align.LEFT),
                new DocxDocument.Cell(box, DocxDocument.Align.LEFT))), false, false, true, null);
        }
    }

    private static DocxDocument.Run plain(String text) {
        return new DocxDocument.Run(text, false, DocxDocument.BODY, null);
    }

    private static List<DocxDocument.Cell> row(String[] cells, boolean bold, String color) {
        List<DocxDocument.Cell> row = new ArrayList<>();
        for (int index = 0; index < cells.length; index++) {
            row.add(new DocxDocument.Cell(List.of(List.of(new DocxDocument.Run(cells[index], bold, DocxDocument.BODY, bold ? color : null))),
                index == 0 ? DocxDocument.Align.LEFT : DocxDocument.Align.RIGHT));
        }
        return row;
    }

    /** Column widths from shares of the page's width. */
    private static int[] columns(double... shares) {
        int[] widths = new int[shares.length];
        for (int index = 0; index < shares.length; index++) {
            widths[index] = (int) Math.round(DocxDocument.CONTENT_WIDTH * shares[index]);
        }
        return widths;
    }
}
