package dev.swirlit.indezy.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes text, lines and shaded boxes from the top of a page downwards, starting a new page when the next block does
 * not fit. Text is drawn with embedded fonts; characters they cannot draw are replaced by a question mark.
 */
final class PdfPageWriter implements Closeable {

    static final float MARGIN = 40;
    static final float SHADE = 0.92f;

    private final PDDocument document;
    private final PDRectangle pageSize;
    private final PDFont regular;
    private final PDFont bold;
    private final Map<Integer, Boolean> drawableRegular = new HashMap<>();
    private final Map<Integer, Boolean> drawableBold = new HashMap<>();
    private PDPageContentStream content;
    private float cursor;

    PdfPageWriter(PDDocument document, PDRectangle pageSize, PDFont regular, PDFont bold) throws IOException {
        this.document = document;
        this.pageSize = pageSize;
        this.regular = regular;
        this.bold = bold;
        newPage();
    }

    float left() {
        return MARGIN;
    }

    float right() {
        return pageSize.getWidth() - MARGIN;
    }

    float width() {
        return right() - left();
    }

    /** The current vertical position, from the bottom of the page. */
    float cursor() {
        return cursor;
    }

    void moveDown(float height) {
        cursor -= height;
    }

    /** Starts a new page when a block of this height does not fit above the bottom margin; true when it did. */
    boolean ensureSpace(float height) throws IOException {
        if (cursor - height < MARGIN) {
            newPage();
            return true;
        }
        return false;
    }

    void text(float x, float baseline, String text, boolean useBold, float size) throws IOException {
        String drawableText = sanitize(text, useBold);
        if (drawableText.isEmpty()) {
            return;
        }
        content.beginText();
        content.setFont(font(useBold), size);
        content.newLineAtOffset(x, baseline);
        content.showText(drawableText);
        content.endText();
    }

    void textCentered(float centerX, float baseline, String text, boolean useBold, float size) throws IOException {
        text(centerX - textWidth(text, useBold, size) / 2, baseline, text, useBold, size);
    }

    void textRight(float rightX, float baseline, String text, boolean useBold, float size) throws IOException {
        text(rightX - textWidth(text, useBold, size), baseline, text, useBold, size);
    }

    float textWidth(String text, boolean useBold, float size) throws IOException {
        return font(useBold).getStringWidth(sanitize(text, useBold)) / 1000 * size;
    }

    void shade(float x, float y, float boxWidth, float height) throws IOException {
        content.setNonStrokingColor(SHADE, SHADE, SHADE);
        content.addRect(x, y, boxWidth, height);
        content.fill();
        content.setNonStrokingColor(0f, 0f, 0f);
    }

    void box(float x, float y, float boxWidth, float height) throws IOException {
        content.setLineWidth(0.5f);
        content.addRect(x, y, boxWidth, height);
        content.stroke();
    }

    void line(float x1, float y1, float x2, float y2) throws IOException {
        content.setLineWidth(0.5f);
        content.moveTo(x1, y1);
        content.lineTo(x2, y2);
        content.stroke();
    }

    /** Writes the text wrapped to the width, one line after the other from the cursor, then moves below it. */
    void paragraph(String text, boolean useBold, float size, float maxWidth) throws IOException {
        float lineHeight = size * 1.35f;
        for (String line : wrap(text, useBold, size, maxWidth)) {
            ensureSpace(lineHeight);
            moveDown(lineHeight);
            text(left(), cursor, line, useBold, size);
        }
    }

    /** Splits the text into lines no wider than the width, keeping its own line breaks and cutting overlong words. */
    List<String> wrap(String text, boolean useBold, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R")) {
            String line = "";
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (textWidth(candidate, useBold, size) <= maxWidth) {
                    line = candidate;
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line);
                }
                line = word;
                while (line.length() > 1 && textWidth(line, useBold, size) > maxWidth) {
                    int fit = fittingLength(line, useBold, size, maxWidth);
                    lines.add(line.substring(0, fit));
                    line = line.substring(fit);
                }
            }
            lines.add(line);
        }
        return lines;
    }

    /** Shortens the text with an ellipsis until it fits the width. */
    String ellipsize(String text, boolean useBold, float size, float maxWidth) throws IOException {
        if (text == null || textWidth(text, useBold, size) <= maxWidth) {
            return text;
        }
        String shortened = text;
        while (shortened.length() > 1 && textWidth(shortened + "…", useBold, size) > maxWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }

    @Override
    public void close() throws IOException {
        content.close();
    }

    private void newPage() throws IOException {
        if (content != null) {
            content.close();
        }
        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        cursor = pageSize.getHeight() - MARGIN;
    }

    private PDFont font(boolean useBold) {
        return useBold ? bold : regular;
    }

    /** How many leading characters of the text fit the width, at least one. */
    private int fittingLength(String text, boolean useBold, float size, float maxWidth) throws IOException {
        int length = 1;
        while (length < text.length() && textWidth(text.substring(0, length + 1), useBold, size) <= maxWidth) {
            length++;
        }
        return length;
    }

    /** Drops control characters and replaces the ones the font has no glyph for, which PDFBox refuses to draw. */
    private String sanitize(String text, boolean useBold) {
        if (text == null) {
            return "";
        }
        PDFont font = font(useBold);
        Map<Integer, Boolean> drawable = useBold ? drawableBold : drawableRegular;
        StringBuilder sanitized = new StringBuilder();
        text.codePoints().filter(codePoint -> !Character.isISOControl(codePoint)).forEach(codePoint ->
            sanitized.appendCodePoint(drawable.computeIfAbsent(codePoint, point -> canDraw(font, point)) ? codePoint : '?'));
        return sanitized.toString();
    }

    private static boolean canDraw(PDFont font, int codePoint) {
        try {
            font.encode(new String(Character.toChars(codePoint)));
            return true;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }
}
