package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.Quote;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Renders a quote as an A4 PDF with Apache PDFBox and the embedded Liberation Sans fonts (ADR 0008). */
@Component
public final class QuotePdfRenderer {

    private final byte[] regularFont = PdfFonts.read(PdfFonts.REGULAR);
    private final byte[] boldFont = PdfFonts.read(PdfFonts.BOLD);

    /** The quote as it states the seller, the buyer and the payment term: frozen once sent, the live ones for a draft. */
    public byte[] render(Quote quote, InvoiceSeller seller, InvoiceBuyer buyer, Integer paymentTermDays) {
        try (PDDocument document = new PDDocument()) {
            PDFont regular = PDType0Font.load(document, new ByteArrayInputStream(regularFont));
            PDFont bold = PDType0Font.load(document, new ByteArrayInputStream(boldFont));
            QuotePage page;
            try (PdfPageWriter writer = new PdfPageWriter(document, PDRectangle.A4, regular, bold)) {
                page = new QuotePage(writer, quote, seller, buyer, paymentTermDays);
                page.write();
            }
            PDDocumentInformation information = document.getDocumentInformation();
            information.setTitle(page.title());
            information.setAuthor(seller.getName());
            information.setCreator("Indezy");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render the quote", e);
        }
    }
}
