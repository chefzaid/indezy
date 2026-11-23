package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DocumentTemplateKind;
import dev.swirlit.indezy.model.enums.LogoPlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A Word template to start from (see docs/features.md#invoices): the built-in layout of an invoice or a quote, in the
 * seller's font, accent colour and footer, with every placeholder where its value goes, so it fills as the built-in
 * documents read once uploaded, and is edited from there.
 */
public final class StarterTemplate {

    private static final int[] HEADER = {DocxDocument.CONTENT_WIDTH / 2, DocxDocument.CONTENT_WIDTH - DocxDocument.CONTENT_WIDTH / 2};
    private static final int[] LINES = columns(0.56, 0.10, 0.14, 0.08, 0.12);
    private static final int[] SIGNATURE = columns(0.5, 0.5);
    private static final int SIGNATURE_LINES = 5;

    private StarterTemplate() {
    }

    /** The template of a kind, in a language, styled as the seller's documents. */
    public static byte[] write(DocumentTemplateKind kind, DocumentLanguage language, InvoiceSeller seller) {
        boolean english = language == DocumentLanguage.EN;
        boolean quote = kind == DocumentTemplateKind.QUOTE;
        String accent = seller.getAccentColor() == null ? null : seller.getAccentColor().substring(1).toUpperCase(Locale.ROOT);
        DocxDocument docx = new DocxDocument(BillingFonts.family(seller), seller.getFooter());
        LogoPlacement placement = seller.getLogoPlacement() == null ? LogoPlacement.LEFT : seller.getLogoPlacement();
        docx.paragraph(List.of(plain("{{logo}}")), switch (placement) {
            case LEFT -> DocxDocument.Align.LEFT;
            case CENTER -> DocxDocument.Align.CENTER;
            case RIGHT -> DocxDocument.Align.RIGHT;
        }, 120);

        List<List<DocxDocument.Run>> sellerBlock = List.of(List.of(new DocxDocument.Run("{{seller.name}}", true, DocxDocument.NAME, null)),
            List.of(plain("{{seller.details}}")));
        List<List<DocxDocument.Run>> heading = new ArrayList<>();
        heading.add(List.of(new DocxDocument.Run("{{document.title}}", true, DocxDocument.TITLE, accent)));
        heading.add(List.of(plain((english ? "No. " : "N° ") + "{{document.number}}")));
        heading.add(List.of(plain((english ? "Date: " : "Date : ") + "{{document.issueDate}}")));
        heading.add(List.of(plain(quote ? (english ? "Valid until: " : "Valable jusqu'au : ") + "{{document.validUntil}}" :
            (english ? "Due: " : "Échéance : ") + "{{document.dueDate}}")));
        heading.add(List.of(new DocxDocument.Run("{{document.draft}}", true, DocxDocument.BODY, null)));
        docx.table(HEADER, List.of(List.of(new DocxDocument.Cell(sellerBlock, DocxDocument.Align.LEFT),
            new DocxDocument.Cell(heading, DocxDocument.Align.RIGHT))), false, false, false, accent);
        docx.paragraph("", false, DocxDocument.BODY, DocxDocument.Align.LEFT);
        docx.paragraph(quote ? (english ? "For" : "Destinataire") : (english ? "Billed to" : "Facturé à"), false, DocxDocument.SMALL,
            DocxDocument.Align.LEFT);
        docx.paragraph(List.of(new DocxDocument.Run("{{client.name}}", true, DocxDocument.NAME, null)), DocxDocument.Align.LEFT, 20);
        docx.paragraph(List.of(plain("{{client.details}}")), DocxDocument.Align.LEFT, 20);
        docx.paragraph("", false, DocxDocument.BODY, DocxDocument.Align.LEFT);

        String[] headings = english ? new String[] {"Description", "Qty", "Unit price", "VAT", "Amount"} :
            new String[] {"Désignation", "Qté", "Prix unitaire", "TVA", "Montant"};
        String[] line = {"{{#lines}}{{line.description}}", "{{line.quantity}}", "{{line.unitPrice}}", "{{line.vatRate}}", "{{line.amount}}{{/lines}}"};
        docx.table(LINES, List.of(row(headings, true, accent), row(line, false, null)), true, true, false, accent);

        docx.paragraph("", false, DocxDocument.BODY, DocxDocument.Align.LEFT);
        docx.paragraph(List.of(plain((english ? "Total excluding VAT: " : "Total HT : ") + "{{totals.excludingVat}}")), DocxDocument.Align.RIGHT, 40);
        docx.paragraph(List.of(plain("{{^vatExempt}}" + (english ? "VAT: " : "TVA : ") + "{{totals.vat}}{{/vatExempt}}")), DocxDocument.Align.RIGHT, 40);
        docx.paragraph(List.of(new DocxDocument.Run((english ? "Amount due: " : "Net à payer : ") + "{{totals.toPay}}", true, DocxDocument.NAME,
            accent)), DocxDocument.Align.RIGHT, 120);

        docx.paragraph(List.of(new DocxDocument.Run("{{mentions}}", false, DocxDocument.SMALL, null)), DocxDocument.Align.LEFT, 120);
        docx.paragraph(List.of(new DocxDocument.Run("{{notes}}", false, DocxDocument.SMALL, null)), DocxDocument.Align.LEFT, 120);
        if (quote) {
            docx.paragraph(List.of(new DocxDocument.Run("{{acceptance}}", true, DocxDocument.SMALL, null)), DocxDocument.Align.LEFT, 60);
            List<List<DocxDocument.Run>> box = new ArrayList<>();
            for (int index = 0; index < SIGNATURE_LINES; index++) {
                box.add(List.of(plain("")));
            }
            docx.table(SIGNATURE, List.of(List.of(new DocxDocument.Cell(List.of(), DocxDocument.Align.LEFT),
                new DocxDocument.Cell(box, DocxDocument.Align.LEFT))), false, false, true, null);
        }
        String title = quote ? (english ? "Quote template" : "Modèle de devis") : (english ? "Invoice template" : "Modèle de facture");
        return docx.save(title, seller.getName() == null ? "" : seller.getName());
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
