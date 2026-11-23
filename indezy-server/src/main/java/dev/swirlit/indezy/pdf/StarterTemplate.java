package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.BodyBlock;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DocumentTheme;
import dev.swirlit.indezy.model.enums.DocumentTemplateKind;
import dev.swirlit.indezy.model.enums.LogoPlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A Word template to start from (see docs/features.md#invoices): the built-in layout of an invoice or a quote, in the
 * seller's font, accent colour, footer and body order, with every placeholder where its value goes, so it fills as the built-in
 * documents read once uploaded, and is edited from there.
 */
public final class StarterTemplate {

    private static final int[] HEADER = {DocxDocument.CONTENT_WIDTH / 2, DocxDocument.CONTENT_WIDTH - DocxDocument.CONTENT_WIDTH / 2};
    private static final int[] LINES = columns(0.56, 0.10, 0.14, 0.08, 0.12);
    private static final int[] SIGNATURE = columns(0.5, 0.5);
    private static final int SIGNATURE_LINES = 5;
    /** The band's colour without an accent colour, as the PDF draws it. */
    private static final String MODERN_DEFAULT = "1F2937";
    private static final String WHITE = "FFFFFF";

    private StarterTemplate() {
    }

    /** The template of a kind, in a language, styled as the seller's documents, in their layout. */
    public static byte[] write(DocumentTemplateKind kind, DocumentLanguage language, InvoiceSeller seller) {
        return write(kind, language, seller, seller.getTheme());
    }

    /**
     * The template in one of the built-in layouts: {@code SIMPLE} as the documents read, {@code MODERN} with a band of
     * the accent colour across the top holding the logo and the title in white, the table header and the amount due
     * filled with it, {@code CLASSIC} in a serif font with the logo centred, a rule under the heading and the mentions
     * framed; null for the documents' own layout.
     */
    public static byte[] write(DocumentTemplateKind kind, DocumentLanguage language, InvoiceSeller seller, DocumentTheme theme) {
        DocumentTheme layout = theme == null ? (seller.getTheme() == null ? DocumentTheme.SIMPLE : seller.getTheme()) : theme;
        seller.setTheme(layout);
        boolean english = language == DocumentLanguage.EN;
        boolean quote = kind == DocumentTemplateKind.QUOTE;
        boolean modern = layout == DocumentTheme.MODERN;
        boolean classic = layout == DocumentTheme.CLASSIC;
        String accent = seller.getAccentColor() == null ? null : seller.getAccentColor().substring(1).toUpperCase(Locale.ROOT);
        String band = accent == null ? MODERN_DEFAULT : accent;
        DocxDocument docx = new DocxDocument(BillingFonts.family(seller), seller.getFooter());
        LogoPlacement placement = classic ? LogoPlacement.CENTER : seller.getLogoPlacement() == null ? LogoPlacement.LEFT : seller.getLogoPlacement();
        DocxDocument.Align logoAlign = switch (placement) {
            case LEFT -> DocxDocument.Align.LEFT;
            case CENTER -> DocxDocument.Align.CENTER;
            case RIGHT -> DocxDocument.Align.RIGHT;
        };
        if (modern) {
            docx.table(new int[] {DocxDocument.CONTENT_WIDTH / 2, DocxDocument.CONTENT_WIDTH - DocxDocument.CONTENT_WIDTH / 2},
                List.of(List.of(new DocxDocument.Cell(List.of(List.of(plain("{{logo}}"))), DocxDocument.Align.LEFT, band),
                    new DocxDocument.Cell(List.of(List.of(new DocxDocument.Run("{{document.title}}", true, DocxDocument.TITLE, WHITE))),
                        DocxDocument.Align.RIGHT, band))), false, false, false, null);
            docx.paragraph("", false, DocxDocument.BODY, DocxDocument.Align.LEFT);
        } else {
            docx.paragraph(List.of(plain("{{logo}}")), logoAlign, 120);
        }

        List<List<DocxDocument.Run>> sellerBlock = List.of(List.of(new DocxDocument.Run("{{seller.name}}", true, DocxDocument.NAME, null)),
            List.of(plain("{{seller.details}}")));
        List<List<DocxDocument.Run>> heading = new ArrayList<>();
        if (!modern) {
            heading.add(List.of(new DocxDocument.Run("{{document.title}}", true, DocxDocument.TITLE, accent)));
        }
        heading.add(List.of(plain((english ? "No. " : "N° ") + "{{document.number}}")));
        heading.add(List.of(plain((english ? "Date: " : "Date : ") + "{{document.issueDate}}")));
        heading.add(List.of(plain(quote ? (english ? "Valid until: " : "Valable jusqu'au : ") + "{{document.validUntil}}" :
            (english ? "Due: " : "Échéance : ") + "{{document.dueDate}}")));
        heading.add(List.of(new DocxDocument.Run("{{document.draft}}", true, DocxDocument.BODY, null)));
        docx.table(HEADER, List.of(List.of(new DocxDocument.Cell(sellerBlock, DocxDocument.Align.LEFT),
            new DocxDocument.Cell(heading, DocxDocument.Align.RIGHT))), false, false, false, accent);
        if (classic) {
            docx.rule(accent);
        } else {
            docx.paragraph("", false, DocxDocument.BODY, DocxDocument.Align.LEFT);
        }
        docx.paragraph(quote ? (english ? "For" : "Destinataire") : (english ? "Billed to" : "Facturé à"), false, DocxDocument.SMALL,
            DocxDocument.Align.LEFT);
        docx.paragraph(List.of(new DocxDocument.Run("{{client.name}}", true, DocxDocument.NAME, null)), DocxDocument.Align.LEFT, 20);
        docx.paragraph(List.of(plain("{{client.details}}")), DocxDocument.Align.LEFT, 20);
        docx.paragraph("", false, DocxDocument.BODY, DocxDocument.Align.LEFT);

        for (BodyBlock block : BodyBlock.read(seller.getBodyBlocks())) {
            switch (block) {
                case LINES -> lines(docx, english, accent, modern ? band : null);
                case MENTIONS -> mentions(docx, classic);
                case NOTES -> docx.paragraph(List.of(new DocxDocument.Run("{{notes}}", false, DocxDocument.SMALL, null)),
                    DocxDocument.Align.LEFT, 120);
                default -> {
                    // SIGNATURE: a quote's box only.
                    if (quote) {
                        signature(docx);
                    }
                }
            }
        }
        String title = quote ? (english ? "Quote template" : "Modèle de devis") : (english ? "Invoice template" : "Modèle de facture");
        return docx.save(title, seller.getName() == null ? "" : seller.getName());
    }

    /** The lines table, its row repeated for each line, then the totals; {@code band} fills the header row and the amount due. */
    private static void lines(DocxDocument docx, boolean english, String accent, String band) {
        String[] headings = english ? new String[] {"Description", "Qty", "Unit price", "VAT", "Amount"} :
            new String[] {"Désignation", "Qté", "Prix unitaire", "TVA", "Montant"};
        String[] line = {"{{#lines}}{{line.description}}", "{{line.quantity}}", "{{line.unitPrice}}", "{{line.vatRate}}", "{{line.amount}}{{/lines}}"};
        List<DocxDocument.Cell> header = band == null ? row(headings, true, accent) : filled(headings, band);
        docx.table(LINES, List.of(header, row(line, false, null)), true, true, false, accent);

        docx.paragraph("", false, DocxDocument.BODY, DocxDocument.Align.LEFT);
        docx.paragraph(List.of(plain((english ? "Total excluding VAT: " : "Total HT : ") + "{{totals.excludingVat}}")), DocxDocument.Align.RIGHT, 40);
        docx.paragraph(List.of(plain("{{^vatExempt}}" + (english ? "VAT: " : "TVA : ") + "{{totals.vat}}{{/vatExempt}}")), DocxDocument.Align.RIGHT, 40);
        String due = (english ? "Amount due: " : "Net à payer : ") + "{{totals.toPay}}";
        if (band == null) {
            docx.paragraph(List.of(new DocxDocument.Run(due, true, DocxDocument.NAME, accent)), DocxDocument.Align.RIGHT, 120);
        } else {
            docx.table(new int[] {DocxDocument.CONTENT_WIDTH / 2, DocxDocument.CONTENT_WIDTH - DocxDocument.CONTENT_WIDTH / 2},
                List.of(List.of(new DocxDocument.Cell(List.of(), DocxDocument.Align.LEFT),
                    new DocxDocument.Cell(List.of(List.of(new DocxDocument.Run(due, true, DocxDocument.NAME, WHITE))), DocxDocument.Align.RIGHT,
                        band))), false, false, false, null);
            docx.paragraph("", false, DocxDocument.BODY, DocxDocument.Align.LEFT);
        }
    }

    /** The mentions placeholder, framed in the classic layout. */
    private static void mentions(DocxDocument docx, boolean framed) {
        DocxDocument.Run mentions = new DocxDocument.Run("{{mentions}}", false, DocxDocument.SMALL, null);
        if (framed) {
            docx.table(new int[] {DocxDocument.CONTENT_WIDTH}, List.of(List.of(new DocxDocument.Cell(List.of(List.of(mentions)),
                DocxDocument.Align.LEFT))), false, false, true, null);
            docx.paragraph("", false, DocxDocument.SMALL, DocxDocument.Align.LEFT);
        } else {
            docx.paragraph(List.of(mentions), DocxDocument.Align.LEFT, 120);
        }
    }

    /** A header row filled with the band's colour, its words in white. */
    private static List<DocxDocument.Cell> filled(String[] cells, String band) {
        List<DocxDocument.Cell> row = new ArrayList<>();
        for (int index = 0; index < cells.length; index++) {
            row.add(new DocxDocument.Cell(List.of(List.of(new DocxDocument.Run(cells[index], true, DocxDocument.BODY, WHITE))),
                index == 0 ? DocxDocument.Align.LEFT : DocxDocument.Align.RIGHT, band));
        }
        return row;
    }

    /** The words the client writes, then the box they sign in. */
    private static void signature(DocxDocument docx) {
        docx.paragraph(List.of(new DocxDocument.Run("{{acceptance}}", true, DocxDocument.SMALL, null)), DocxDocument.Align.LEFT, 60);
        List<List<DocxDocument.Run>> box = new ArrayList<>();
        for (int index = 0; index < SIGNATURE_LINES; index++) {
            box.add(List.of(plain("")));
        }
        docx.table(SIGNATURE, List.of(List.of(new DocxDocument.Cell(List.of(), DocxDocument.Align.LEFT),
            new DocxDocument.Cell(box, DocxDocument.Align.LEFT))), false, false, true, null);
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

    private static int[] columns(double... shares) {
        int[] widths = new int[shares.length];
        for (int index = 0; index < shares.length; index++) {
            widths[index] = (int) Math.round(DocxDocument.CONTENT_WIDTH * shares[index]);
        }
        return widths;
    }
}
