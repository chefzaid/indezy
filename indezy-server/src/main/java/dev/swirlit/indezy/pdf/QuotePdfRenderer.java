package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.DocumentLogo;
import dev.swirlit.indezy.model.InvoiceBuyer;
import dev.swirlit.indezy.model.InvoiceSeller;
import dev.swirlit.indezy.model.Quote;
import dev.swirlit.indezy.model.DocumentLetterhead;
import dev.swirlit.indezy.repository.DocumentFontFileRepository;
import dev.swirlit.indezy.repository.DocumentLetterheadRepository;
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
    private DocumentLetterheadRepository letterheads;
    private DocumentLogoRepository logos;

    /** Where the sellers' logos are read; without it, as in tests, documents print none. */
    @Autowired(required = false)
    public void setLogos(DocumentLogoRepository logos) {
        this.logos = logos;
    }

    /** Where the letterheads are read; without it, as in tests, documents are drawn on blank pages. */
    @Autowired(required = false)
    public void setLetterheads(DocumentLetterheadRepository repository) {
        this.letterheads = repository;
    }

    /** The room the letterhead asks to keep free; the usual margins without one. */
    private static PdfPageWriter.Margins margins(InvoiceSeller seller, boolean letterhead) {
        return letterhead ? PdfPageWriter.Margins.ofMillimetres(seller.getLetterheadTopMm(), seller.getLetterheadBottomMm()) :
            new PdfPageWriter.Margins(PdfPageWriter.MARGIN, PdfPageWriter.MARGIN);
    }

    private byte[] letterheadOf(InvoiceSeller seller) {
        return letterheads == null || seller.getLetterheadId() == null ? null :
            letterheads.findById(seller.getLetterheadId()).map(DocumentLetterhead::getContent).orElse(null);
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
        return renderDocx(new QuotePage(null, quote, seller, buyer, paymentTermDays, terms, termsFile), null);
    }

    /** As above, filling the freelancer's Word template when given one instead of the built-in layout. */
    public byte[] renderDocx(Quote quote, InvoiceSeller seller, InvoiceBuyer buyer, Integer paymentTermDays, String terms, boolean termsFile,
                             byte[] template) {
        return renderDocx(new QuotePage(null, quote, seller, buyer, paymentTermDays, terms, termsFile), template);
    }

    private byte[] renderDocx(QuotePage page, byte[] template) {
        return template == null ? BillingDocx.render(page, logoOf(page.seller), page.title()) :
            PdfFormTemplate.isPdf(template) ? PdfFormTemplate.fill(template, page) : DocxTemplate.fill(template, page, logoOf(page.seller));
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
        byte[] letterheadPdf = letterheadOf(seller);
        try (PDDocument document = new PDDocument(); Letterhead letterhead = letterheadPdf == null ? null : Letterhead.open(letterheadPdf, document)) {
            BillingFonts.Faces faces = fonts.of(seller, uploadedFonts);
            PDFont regular = PDType0Font.load(document, new ByteArrayInputStream(faces.regular()));
            PDFont bold = PDType0Font.load(document, new ByteArrayInputStream(faces.bold()));
            QuotePage page;
            try (PdfPageWriter writer = new PdfPageWriter(document, PDRectangle.A4, regular, bold, margins(seller, letterhead != null))) {
                page = new QuotePage(writer, quote, seller, buyer, paymentTermDays, terms, termsPdf != null);
                writer.zones(page.zones());
                page.logo(logoOf(seller));
                page.write();
            }
            // Under the quote's own pages, not under the terms and conditions PDF appended after them.
            if (letterhead != null) {
                letterhead.drawUnder(document);
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
