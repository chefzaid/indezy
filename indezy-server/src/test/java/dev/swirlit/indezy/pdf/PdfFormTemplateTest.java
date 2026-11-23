package dev.swirlit.indezy.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PdfFormTemplateTest {

    /** A one-page form with a text field for each name, one under the other. */
    public static byte[] form(List<String> names) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDAcroForm form = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(form);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            form.setDefaultResources(resources);
            float y = 760;
            java.util.Map<String, org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField> parents = new java.util.HashMap<>();
            for (String name : names) {
                PDTextField field = new PDTextField(form);
                int dot = name.indexOf('.');
                field.setPartialName(dot < 0 ? name : name.substring(dot + 1));
                field.setDefaultAppearance("/Helv 9 Tf 0 g");
                field.setMultiline(true);
                PDAnnotationWidget widget = field.getWidgets().getFirst();
                widget.setRectangle(new PDRectangle(50, y, 400, 60));
                widget.setPage(page);
                page.getAnnotations().add(widget);
                if (dot < 0) {
                    form.getFields().add(field);
                } else {
                    // A field inside another: invoice > number reads invoice.number.
                    org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField parent = parents.computeIfAbsent(name.substring(0, dot), partial -> {
                        org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField created = new org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField(form);
                        created.setPartialName(partial);
                        form.getFields().add(created);
                        return created;
                    });
                    java.util.List<org.apache.pdfbox.pdmodel.interactive.form.PDField> kids = new java.util.ArrayList<>(parent.getChildren());
                    kids.add(field);
                    parent.setChildren(kids);
                    field.getCOSObject().setItem(COSName.PARENT, parent.getCOSObject());
                }
                y -= 70;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void readsTheFormsFieldsAndTheMentionsItLacks() throws IOException {
        DocxTemplate.Analysis analysis = PdfFormTemplate.analyse(form(List.of("invoice.number", "client.name", "line_description_1",
            "line_amount_1", "totals_toPay", "Signature")));
        assertThat(analysis.found()).containsExactly("invoice.number", "client.name", "line_description_1", "line_amount_1", "totals_toPay");
        assertThat(analysis.unknown()).containsExactly("Signature");
        assertThat(analysis.missing()).containsExactly("date", "seller", "mentions");
        assertThat(PdfFormTemplate.isPdf(form(List.of("notes")))).isTrue();
    }

    @Test
    void fillsTheFieldsInItsFontFlattensThemAndPutsWhatHasNoRoomOnAPageAfter() throws IOException {
        byte[] form = form(List.of("invoice.number", "document_issueDate", "seller_details", "client.name", "line_description_1", "line_amount_1",
            "totals_toPay"));
        InvoicePage page = new InvoicePage(null, DocxTemplateTest.invoice(), DocxTemplateTest.seller(), DocxTemplateTest.buyer());

        byte[] filled = PdfFormTemplate.fill(form, page);

        try (PDDocument document = Loader.loadPDF(filled)) {
            assertThat(document.getDocumentCatalog().getAcroForm() == null || document.getDocumentCatalog().getAcroForm().getFields().isEmpty()).isTrue();
            assertThat(document.getNumberOfPages()).isEqualTo(2);
        }
        String text = text(filled);
        // Its fields, "€" and the narrow spaces of French amounts drawn in Indezy's font.
        assertThat(text).contains("F2026-0007", "1 décembre 2026", "Société Générale & Fils", "Développement Java", "12 025,00 €", "14 574,00 €");
        // The second line has no row: it goes after, with the mentions the form has no field for.
        assertThat(text.substring(text.indexOf("14 574,00"))).contains("Frais <déplacement>", "Pas d'escompte pour paiement anticipé");
    }

    @Test
    void refusesAPdfWithoutFields() throws IOException {
        assertThatThrownBy(() -> PdfFormTemplate.analyse(form(List.of()))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no form field");
        assertThatThrownBy(() -> PdfFormTemplate.analyse("%PDF-1.7 broken".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(PdfFormTemplate.key("line.amount")).isNull();
        assertThat(PdfFormTemplate.key("quote.validUntil")).isEqualTo("document.validUntil");
        assertThat(PdfFormTemplate.key("line.vatRate.12")).isEqualTo("line.vatRate");
        assertThat(PdfFormTemplate.key("client.name.2")).isNull();
    }
}
