package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;

/** Renders a dossier de compétences as an A4 PDF with Apache PDFBox and the embedded Liberation Sans fonts (ADR 0008). */
@Component
public final class DossierPdfRenderer {

    private final byte[] regularFont = PdfFonts.read(PdfFonts.REGULAR);
    private final byte[] boldFont = PdfFonts.read(PdfFonts.BOLD);

    /** The same dossier as an editable Word document (a {@code .docx} file), for an ESN to paste into its template. */
    public byte[] docx(DossierDto dossier, DocumentLanguage language, LocalDate today) {
        return docx(dossier, language, today, DossierLook.CLASSIC);
    }

    public byte[] docx(DossierDto dossier, DocumentLanguage language, LocalDate today, DossierLook look) {
        return DossierDocx.render(dossier, language, today, look);
    }

    public byte[] render(DossierDto dossier, DocumentLanguage language, LocalDate today) {
        return render(dossier, language, today, DossierLook.CLASSIC);
    }

    /** The first page of the dossier as a PNG image, {@code dpi} dots an inch: what it looks like, before downloading it. */
    public byte[] preview(DossierDto dossier, DocumentLanguage language, LocalDate today, DossierLook look, float dpi) {
        try (PDDocument document = Loader.loadPDF(render(dossier, language, today, look))) {
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, dpi, ImageType.RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not preview the dossier", e);
        }
    }

    public byte[] render(DossierDto dossier, DocumentLanguage language, LocalDate today, DossierLook look) {
        try (PDDocument document = new PDDocument()) {
            PDFont regular = PDType0Font.load(document, new ByteArrayInputStream(regularFont));
            PDFont bold = PDType0Font.load(document, new ByteArrayInputStream(boldFont));
            DossierPage page;
            try (PdfPageWriter writer = new PdfPageWriter(document, PDRectangle.A4, regular, bold)) {
                page = new DossierPage(writer, dossier, language, look);
                page.write(today);
            }
            PDDocumentInformation information = document.getDocumentInformation();
            information.setTitle(page.title());
            information.setAuthor(dossier.name());
            information.setCreator("Indezy");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not render the dossier", e);
        }
    }
}
