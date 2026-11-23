package dev.swirlit.indezy.pdf;

import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.OS2WindowsMetricsTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;

import java.io.IOException;

/**
 * Whether an uploaded font can print invoices and quotes: a TrueType font (OpenType with CFF outlines cannot be embedded
 * here), whose licence lets it be embedded in a document (its OS/2 {@code fsType} neither restricted nor bitmap-only:
 * Factur-X needs every font embedded) and which draws the letters of French invoices.
 */
public final class FontCheck {

    /** The letters a French invoice prints, besides the plain ones. */
    private static final String NEEDED = "AZaz09€éèêàâçôùûîïëÉÈÀÇ%:;,.-«»'’";
    private static final int RESTRICTED = 0x0002;
    private static final int LICENSE_BITS = 0x000F;
    private static final int BITMAP_ONLY = 0x0200;

    private FontCheck() {
    }

    /** The font's family name; refused with the reason when it cannot print the documents. */
    public static String family(byte[] bytes) {
        if (bytes.length > 4 && bytes[0] == 'O' && bytes[1] == 'T' && bytes[2] == 'T' && bytes[3] == 'O') {
            throw new IllegalArgumentException("This OpenType font draws its letters with CFF outlines, which cannot be embedded: send a TrueType " +
                "font (.ttf, or an .otf with TrueType outlines)");
        }
        try (TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(bytes))) {
            OS2WindowsMetricsTable os2 = font.getOS2Windows();
            if (os2 != null) {
                int fsType = os2.getFsType();
                if ((fsType & LICENSE_BITS) == RESTRICTED || (fsType & BITMAP_ONLY) != 0) {
                    throw new IllegalArgumentException("This font's licence does not allow embedding it in a document: choose another one");
                }
            }
            CmapLookup cmap = font.getUnicodeCmapLookup(false);
            if (cmap == null) {
                throw new IllegalArgumentException("This font has no Unicode letters: choose another one");
            }
            StringBuilder missing = new StringBuilder();
            NEEDED.codePoints().filter(point -> cmap.getGlyphId(point) == 0).forEach(missing::appendCodePoint);
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("This font lacks letters an invoice prints: " + missing);
            }
            String family = font.getNaming() == null ? null : font.getNaming().getFontFamily();
            return family == null || family.isBlank() ? "Custom" : family.strip();
        } catch (IOException e) {
            throw new IllegalArgumentException("This file is not a font that can be read: send a TrueType font (.ttf)", e);
        }
    }
}
