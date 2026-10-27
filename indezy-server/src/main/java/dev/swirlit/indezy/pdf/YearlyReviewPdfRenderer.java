package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.dto.YearlyReviewDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
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
import java.time.LocalDate;

/** Renders a yearly review as an A4 PDF with Apache PDFBox and the embedded Liberation Sans fonts (ADR 0008). */
@Component
public final class YearlyReviewPdfRenderer {

    private final byte[] regularFont = PdfFonts.read(PdfFonts.REGULAR);
    private final byte[] boldFont = PdfFonts.read(PdfFonts.BOLD);

    public byte[] render(YearlyReviewDto review, DocumentLanguage language, LocalDate today) {
        try (PDDocument document = new PDDocument()) {
            PDFont regular = PDType0Font.load(document, new ByteArrayInputStream(regularFont));
            PDFont bold = PDType0Font.load(document, new ByteArrayInputStream(boldFont));
            YearlyReviewPage page;
            try (PdfPageWriter writer = new PdfPageWriter(document, PDRectangle.A4, regular, bold)) {
                page = new YearlyReviewPage(writer, review, language);
                page.write(today);
            }
            PDDocumentInformation information = document.getDocumentInformation();
            information.setTitle(page.title());
            information.setAuthor(review.name());
            information.setCreator("Indezy");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render the yearly review", e);
        }
    }
}
