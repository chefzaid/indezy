package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.DocumentLogo;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.Quote;
import dev.swirlit.indezy.repository.DocumentFontFileRepository;
import dev.swirlit.indezy.repository.DocumentLogoRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
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

    /** The quote as a Word file laid out like its PDF, its terms and conditions text after it (not an attached PDF of them). */
    public byte[] renderDocx(Quote quote, InvoiceSeller seller, InvoiceBuyer buyer, Integer paymentTermDays, String terms, boolean termsFile) {
        QuotePage page = new QuotePage(null, quote, seller, buyer, paymentTermDays, terms, termsFile);
        return BillingDocx.render(page, logoOf(seller), page.title());
    }

    /**
     * The quote as it states the seller, the buyer, the payment term and the terms and conditions printed after it:
     * frozen once sent, the live ones for a draft.
     */
    public byte[] render(Quote quote, InvoiceSeller seller, InvoiceBuyer buyer, Integer paymentTermDays, String terms) {
        return render(quote, seller, buyer, paymentTermDays, terms, null);
    }

    /** As above, the terms and conditions PDF's pages appended after the quote when given. */
    public byte[] render(Quote quote, InvoiceSeller seller, InvoiceBuyer buyer, Integer paymentTermDays, String terms, byte[] termsPdf) {
        try (PDDocument document = new PDDocument()) {
            BillingFonts.Faces faces = fonts.of(seller, uploadedFonts);
            PDFont regular = PDType0Font.load(document, new ByteArrayInputStream(faces.regular()));
            PDFont bold = PDType0Font.load(document, new ByteArrayInputStream(faces.bold()));
            QuotePage page;
            try (PdfPageWriter writer = new PdfPageWriter(document, PDRectangle.A4, regular, bold)) {
                page = new QuotePage(writer, quote, seller, buyer, paymentTermDays, terms, termsPdf != null);
                writer.footer(seller.getFooter());
                page.logo(logoOf(seller));
                page.write();
            }
            PDDocumentInformation information = document.getDocumentInformation();
            information.setTitle(page.title());
            information.setAuthor(seller.getName());
            information.setCreator("Indezy");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (termsPdf == null) {
                document.save(output);
                return output.toByteArray();
            }
            // The terms' pages after the quote's, the source kept open until the whole is saved.
            try (PDDocument appended = Loader.loadPDF(termsPdf)) {
                for (PDPage termsPage : appended.getPages()) {
                    document.importPage(termsPage);
                }
                document.save(output);
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render the quote", e);
        }
    }
}
