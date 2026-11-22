package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.DocumentLogo;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceSeller;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Renders an invoice as an A4 PDF with Apache PDFBox and the embedded Liberation Sans fonts (ADR 0008); an issued invoice
 * or credit note as a Factur-X file (PDF/A-3 with its Cross Industry Invoice, see {@link FacturXPdf}).
 */
@Component
public final class InvoicePdfRenderer {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

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

    /** The invoice as it states the seller and the buyer: frozen once issued, the live ones for a draft. */
    public byte[] render(Invoice invoice, InvoiceSeller seller, InvoiceBuyer buyer) {
        try (PDDocument document = new PDDocument()) {
            PDFont regular = PDType0Font.load(document, new ByteArrayInputStream(regularFont));
            PDFont bold = PDType0Font.load(document, new ByteArrayInputStream(boldFont));
            InvoicePage page;
            try (PdfPageWriter writer = new PdfPageWriter(document, PDRectangle.A4, regular, bold)) {
                page = new InvoicePage(writer, invoice, seller, buyer);
                page.logo(logoOf(seller));
                page.write();
            }
            if (invoice.getNumber() == null) {
                PDDocumentInformation information = document.getDocumentInformation();
                information.setTitle(page.title());
                information.setAuthor(seller.getName());
                information.setCreator("Indezy");
            } else {
                // Issued: a Factur-X file, the printed invoice with its structured data.
                FacturXPdf.apply(document, page.title(), seller.getName() == null ? "" : seller.getName(),
                    FacturXml.of(invoice, seller, buyer), ZonedDateTime.now(PARIS));
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render the invoice", e);
        }
    }
}
