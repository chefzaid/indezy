package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.DocumentLogo;
import dev.swirlit.indezy.model.Invoice;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.repository.DocumentFontFileRepository;
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

    private final BillingFonts fonts = new BillingFonts();
    private DocumentFontFileRepository uploadedFonts;
    private DocumentLogoRepository logos;

    /** Where the sellers' logos are read; without it, as in tests, documents print none. */
    @Autowired(required = false)
    public void setLogos(DocumentLogoRepository logos) {
        this.logos = logos;
    }

    /** Where the uploaded fonts are read; without it, as in tests, documents print in the bundled ones. */
    @Autowired(required = false)
    public void setFonts(DocumentFontFileRepository repository) {
        this.uploadedFonts = repository;
    }

    private byte[] logoOf(InvoiceSeller seller) {
        return logos == null || seller.getLogoId() == null ? null :
            logos.findById(seller.getLogoId()).map(DocumentLogo::getContent).orElse(null);
    }

    /** The invoice as a Word file laid out like its PDF: a copy to read or archive, the PDF staying the invoice sent. */
    public byte[] renderDocx(Invoice invoice, InvoiceSeller seller, InvoiceBuyer buyer) {
        return renderDocx(invoice, seller, buyer, null);
    }

    /** As above, filling the freelancer's Word template when given one instead of the built-in layout. */
    public byte[] renderDocx(Invoice invoice, InvoiceSeller seller, InvoiceBuyer buyer, byte[] template) {
        InvoicePage page = new InvoicePage(null, invoice, seller, buyer);
        return template == null ? BillingDocx.render(page, logoOf(seller), page.title()) : DocxTemplate.fill(template, page, logoOf(seller));
    }

    /** The invoice as it states the seller and the buyer: frozen once issued, the live ones for a draft. */
    public byte[] render(Invoice invoice, InvoiceSeller seller, InvoiceBuyer buyer) {
        try (PDDocument document = new PDDocument()) {
            BillingFonts.Faces faces = fonts.of(seller, uploadedFonts);
            PDFont regular = PDType0Font.load(document, new ByteArrayInputStream(faces.regular()));
            PDFont bold = PDType0Font.load(document, new ByteArrayInputStream(faces.bold()));
            InvoicePage page;
            try (PdfPageWriter writer = new PdfPageWriter(document, PDRectangle.A4, regular, bold)) {
                page = new InvoicePage(writer, invoice, seller, buyer);
                writer.footer(seller.getFooter());
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
