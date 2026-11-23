package dev.swirlit.indezy.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A letterhead PDF under invoices and quotes: checked on upload (A4, one or two pages, not encrypted, without scripts,
 * its fonts embedded, as PDF/A and so Factur-X ask), then drawn under each page of a document, its first page under the
 * first and its second (or its first again) under the others.
 */
public final class Letterhead implements Closeable {

    /** A4 within two points either way. */
    private static final float TOLERANCE = 2;

    private final PDDocument source;
    private final List<PDFormXObject> forms;

    private Letterhead(PDDocument source, List<PDFormXObject> forms) {
        this.source = source;
        this.forms = List.copyOf(forms);
    }

    /** The letterhead's number of pages; refused with the reason when it cannot sit under the documents. */
    public static int check(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("This PDF is protected: save it without a password");
            }
            int pages = document.getNumberOfPages();
            if (pages == 0 || pages > 2) {
                throw new IllegalArgumentException("A letterhead has one or two pages");
            }
            COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
            boolean scripts = catalog.containsKey(COSName.OPEN_ACTION) || catalog.containsKey(COSName.AA) ||
                document.getDocumentCatalog().getNames() != null && document.getDocumentCatalog().getNames().getJavaScript() != null;
            if (scripts) {
                throw new IllegalArgumentException("This PDF runs scripts or actions: save it without them");
            }
            for (PDPage page : document.getPages()) {
                PDRectangle box = page.getMediaBox();
                boolean a4 = Math.abs(box.getWidth() - PDRectangle.A4.getWidth()) <= TOLERANCE &&
                    Math.abs(box.getHeight() - PDRectangle.A4.getHeight()) <= TOLERANCE;
                if (!a4 || page.getRotation() % 360 != 0) {
                    throw new IllegalArgumentException("A letterhead is an upright A4 page");
                }
                List<String> notEmbedded = new ArrayList<>();
                fonts(page.getResources(), notEmbedded, 0);
                if (!notEmbedded.isEmpty()) {
                    throw new IllegalArgumentException("This PDF uses fonts it does not embed (" + String.join(", ", notEmbedded) +
                        "): export it again with its fonts embedded");
                }
            }
            return pages;
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("This PDF is protected: save it without a password", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("This file is not a PDF that can be read", e);
        }
    }

    /** The fonts a page's resources draw with that are not embedded, its forms looked into. */
    private static void fonts(PDResources resources, List<String> notEmbedded, int depth) throws IOException {
        if (resources == null || depth > 5) {
            return;
        }
        for (COSName name : resources.getFontNames()) {
            PDFont font = resources.getFont(name);
            if (font != null && !font.isEmbedded()) {
                notEmbedded.add(font.getName());
            }
        }
        for (COSName name : resources.getXObjectNames()) {
            PDXObject object = resources.getXObject(name);
            if (object instanceof PDFormXObject form) {
                fonts(form.getResources(), notEmbedded, depth + 1);
            }
        }
    }

    /** The letterhead read for drawing into {@code target}; to close once the target is saved. */
    static Letterhead open(byte[] pdf, PDDocument target) throws IOException {
        PDDocument source = Loader.loadPDF(pdf);
        LayerUtility layers = new LayerUtility(target);
        List<PDFormXObject> forms = new ArrayList<>();
        for (int index = 0; index < Math.min(2, source.getNumberOfPages()); index++) {
            forms.add(layers.importPageAsForm(source, index));
        }
        return new Letterhead(source, forms);
    }

    /** Draws the letterhead under every page of the document. */
    void drawUnder(PDDocument document) throws IOException {
        if (forms.isEmpty()) {
            return;
        }
        int index = 0;
        for (PDPage page : document.getPages()) {
            PDFormXObject form = index == 0 || forms.size() == 1 ? forms.getFirst() : forms.get(1);
            try (PDPageContentStream under = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.PREPEND, true, true)) {
                under.saveGraphicsState();
                under.drawForm(form);
                under.restoreGraphicsState();
            }
            index++;
        }
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}
