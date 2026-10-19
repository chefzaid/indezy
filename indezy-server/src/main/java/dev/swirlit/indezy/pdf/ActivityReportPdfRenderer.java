package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.enums.ActivityReportLayout;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Renders activity reports as PDF with Apache PDFBox (ADR 0008): A4 portrait for the daily table, A4 landscape for the
 * calendar grid. Liberation Sans is embedded (subset) so names and accents print the same everywhere.
 */
@Component
public final class ActivityReportPdfRenderer {

    private static final PDRectangle LANDSCAPE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());

    private final byte[] regularFont = readFont("fonts/LiberationSans-Regular.ttf");
    private final byte[] boldFont = readFont("fonts/LiberationSans-Bold.ttf");

    public byte[] render(ActivityReportDocument report) {
        boolean grid = report.template().layout() == ActivityReportLayout.CALENDAR_GRID;
        try (PDDocument document = new PDDocument()) {
            PDFont regular = PDType0Font.load(document, new ByteArrayInputStream(regularFont));
            PDFont bold = PDType0Font.load(document, new ByteArrayInputStream(boldFont));
            ActivityReportPage page;
            try (PdfPageWriter writer = new PdfPageWriter(document, grid ? LANDSCAPE : PDRectangle.A4, regular, bold)) {
                page = new ActivityReportPage(writer, report);
                page.write();
            }
            PDDocumentInformation information = document.getDocumentInformation();
            information.setTitle(page.title() + " - " + page.monthTitle());
            information.setAuthor(report.consultantName());
            information.setCreator("Indezy");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render the activity report", e);
        }
    }

    private static byte[] readFont(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Missing PDF font " + path, e);
        }
    }
}
