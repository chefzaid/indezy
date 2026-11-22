package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.DocumentLogo;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.Quote;
import dev.swirlit.indezy.repository.DocumentLogoRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Autowired;
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
    private DocumentLogoRepository logos;

    /** Where the sellers' logos are read; without it, as in tests, documents print none. */
    @Autowired(required = false)
    public void setLogos(DocumentLogoRepository logos) {
        this.logos = logos;
    }

    private byte[] logoOf(InvoiceSeller seller) {
        return logos == null || seller.getLogoId() == null ? null :
            logos.findById(seller.getLogoId()).map(DocumentLogo::getContent).orElse(null);
    }

    /**
     * The quote as it states the seller, the buyer, the payment term and the terms and conditions printed after it:
     * frozen once sent, the live ones for a draft.
     */
    public byte[] render(Quote quote, InvoiceSeller seller, InvoiceBuyer buyer, Integer paymentTermDays, String terms) {
        try (PDDocument document = new PDDocument()) {
            PDFont regular = PDType0Font.load(document, new ByteArrayInputStream(regularFont));
            PDFont bold = PDType0Font.load(document, new ByteArrayInputStream(boldFont));
            QuotePage page;
            try (PdfPageWriter writer = new PdfPageWriter(document, PDRectangle.A4, regular, bold)) {
                page = new QuotePage(writer, quote, seller, buyer, paymentTermDays, terms);
                page.logo(logoOf(seller));
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
