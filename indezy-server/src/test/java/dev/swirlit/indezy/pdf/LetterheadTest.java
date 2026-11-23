package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.DocumentLetterhead;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InvoiceStatus;
import dev.swirlit.indezy.repository.DocumentLetterheadRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LetterheadTest {

    /** A letterhead of these pages, each with its words in an embedded font (or a standard one when asked), on this size. */
    public static byte[] letterhead(PDRectangle size, boolean embedded, String... pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFont font = embedded ? PDType0Font.load(document, new ByteArrayInputStream(FontCheckTest.serif())) :
                new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String words : pages) {
                PDPage page = new PDPage(size);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.setNonStrokingColor(0.12f, 0.3f, 0.47f);
                    stream.addRect(0, size.getHeight() - 20, size.getWidth(), 20);
                    stream.fill();
                    stream.beginText();
                    stream.setFont(font, 9);
                    stream.newLineAtOffset(40, 20);
                    stream.showText(words);
                    stream.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void acceptsOneOrTwoA4PagesWithTheirFontsEmbedded() throws IOException {
        assertThat(Letterhead.check(letterhead(PDRectangle.A4, true, "Martin Conseil – 18 rue du Sentier"))).isEqualTo(1);
        assertThat(Letterhead.check(letterhead(PDRectangle.A4, true, "Première page", "Pages suivantes"))).isEqualTo(2);
    }

    @Test
    void refusesWhatCannotSitUnderAFacturXInvoice() throws IOException {
        assertThatThrownBy(() -> Letterhead.check(letterhead(PDRectangle.LETTER, true, "US Letter"))).hasMessageContaining("upright A4");
        assertThatThrownBy(() -> Letterhead.check(letterhead(PDRectangle.A4, true, "1", "2", "3"))).hasMessageContaining("one or two pages");
        assertThatThrownBy(() -> Letterhead.check(letterhead(PDRectangle.A4, false, "Helvetica"))).hasMessageContaining("does not embed");
        try (PDDocument document = Loader.loadPDF(letterhead(PDRectangle.A4, true, "Script")); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('hi')"));
            document.save(out);
            assertThatThrownBy(() -> Letterhead.check(out.toByteArray())).hasMessageContaining("scripts");
        }
        try (PDDocument document = Loader.loadPDF(letterhead(PDRectangle.A4, true, "Locked")); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.protect(new StandardProtectionPolicy("owner", "user", new AccessPermission()));
            document.save(out);
            assertThatThrownBy(() -> Letterhead.check(out.toByteArray())).hasMessageContaining("protected");
        }
        assertThatThrownBy(() -> Letterhead.check("not a pdf".getBytes())).hasMessageContaining("not a PDF");
    }

    @Test
    void drawsTheInvoiceOverItsFirstPageThenItsSecond() throws IOException {
        DocumentLetterhead stored = new DocumentLetterhead();
        stored.setContent(letterhead(PDRectangle.A4, true, "Papier premiere page", "Papier pages suivantes"));
        DocumentLetterheadRepository letterheads = Mockito.mock(DocumentLetterheadRepository.class);
        Mockito.when(letterheads.findById(3L)).thenReturn(Optional.of(stored));
        InvoicePdfRenderer renderer = new InvoicePdfRenderer();
        renderer.setLetterheads(letterheads);
        InvoiceSeller seller = new InvoiceSeller();
        seller.setName("Martin Conseil");
        seller.setSiret("55210055400012");
        seller.setVatExempt(true);
        seller.setLetterheadId(3L);
        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setLanguage(DocumentLanguage.FR);
        invoice.setNumber("F2026-0007");
        invoice.setIssueDate(LocalDate.of(2026, 11, 1));
        invoice.setDueDate(LocalDate.of(2026, 12, 1));
        List<InvoiceLine> lines = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            lines.add(new InvoiceLine("Lot " + index, BigDecimal.ONE, new BigDecimal("650.00"), BigDecimal.ZERO));
        }
        invoice.setLines(lines);
        InvoiceBuyer buyer = new InvoiceBuyer();
        buyer.setName("Banque Exemple");
        try (PDDocument document = Loader.loadPDF(renderer.render(invoice, seller, buyer))) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            assertThat(stripper.getText(document)).contains("Papier premiere page", "F2026-0007").doesNotContain("Papier pages suivantes");
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            assertThat(stripper.getText(document)).contains("Papier pages suivantes");
            // Still a Factur-X file, the letterhead's font embedded with the invoice's.
            for (PDPage page : document.getPages()) {
                for (COSName name : page.getResources().getXObjectNames()) {
                    org.apache.pdfbox.pdmodel.graphics.PDXObject object = page.getResources().getXObject(name);
                    if (object instanceof org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject form) {
                        for (COSName font : form.getResources().getFontNames()) {
                            assertThat(form.getResources().getFont(font).isEmbedded()).isTrue();
                        }
                    }
                }
            }
            assertThat(document.getDocumentCatalog().getNames().getEmbeddedFiles().getNames()).containsKey("factur-x.xml");
        }
    }
}
