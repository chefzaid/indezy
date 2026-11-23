package dev.swirlit.indezy.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** One page of a PDF drawn as a PNG for a preview, with how many pages the PDF has. */
public record PagePreview(byte[] png, int pages) {

    private static final float DPI = 90;

    public PagePreview {
        png = png.clone();
    }

    @Override
    public byte[] png() {
        return png.clone();
    }

    /** The page at {@code number} (from 1, kept within the document) of the PDF, as an image. */
    public static PagePreview of(byte[] pdf, int number) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pages = document.getNumberOfPages();
            int index = Math.max(0, Math.min(pages, number) - 1);
            BufferedImage page = new PDFRenderer(document).renderImageWithDPI(index, DPI);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(page, "png", png);
            return new PagePreview(png.toByteArray(), pages);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not draw the preview", e);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PagePreview preview && preview.pages == pages && java.util.Arrays.equals(preview.png, png);
    }

    @Override
    public int hashCode() {
        return 31 * java.util.Arrays.hashCode(png) + pages;
    }

    @Override
    public String toString() {
        return "PagePreview[" + png.length + " bytes, " + pages + " pages]";
    }
}
