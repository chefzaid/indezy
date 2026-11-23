package dev.swirlit.indezy.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

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
    /** Written at the foot of every page once the document is done, beside its page number; null for none. */
    private Zones zones = new Zones(null, null, null, null, null);
    /** The size of the footer's and the header's zones. */
    private static final float ZONE_SIZE = 7.5f;

    /** The room kept free at the top and the bottom of every page, in points (a letterhead's header and footer). */
    private final float keptTop;
    private final float keptBottom;

    PdfPageWriter(PDDocument document, PDRectangle pageSize, PDFont regular, PDFont bold) throws IOException {
        this(document, pageSize, regular, bold, new Margins(MARGIN, MARGIN));
    }

    PdfPageWriter(PDDocument document, PDRectangle pageSize, PDFont regular, PDFont bold, Margins margins) throws IOException {
        this.document = document;
        this.pageSize = pageSize;
        this.regular = regular;
        this.bold = bold;
        this.keptTop = Math.max(MARGIN, margins.top());
        this.keptBottom = Math.max(MARGIN, margins.bottom());
        newPage();
    }

    /** The room to keep free at the top and the bottom of the pages, in points; never less than {@link #MARGIN}. */
    record Margins(float top, float bottom) {

        private static final float POINTS_PER_MM = 72f / 25.4f;

        /** The margins a letterhead asks for, in millimetres; the usual ones for those it does not set. */
        static Margins ofMillimetres(Integer topMm, Integer bottomMm) {
            return new Margins(topMm == null ? MARGIN : topMm * POINTS_PER_MM, bottomMm == null ? MARGIN : bottomMm * POINTS_PER_MM);
        }
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
        if (cursor - height < keptBottom) {
            newPage();
            return true;
        }
        return false;
    }

    /** The width an image takes once fitted into the box, kept in proportion. */
    float imageWidth(byte[] bytes, float maxWidth, float maxHeight) throws IOException {
        PDImageXObject image = PDImageXObject.createFromByteArray(document, bytes, "logo");
        return image.getWidth() * Math.min(1f, Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight()));
    }

    /**
     * Draws an image with its top left corner at the point, scaled down to fit the box and kept in proportion; the
     * height it took.
     */
    float image(byte[] bytes, float x, float top, float maxWidth, float maxHeight) throws IOException {
        PDImageXObject image = PDImageXObject.createFromByteArray(document, bytes, "logo");
        float scale = Math.min(1f, Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight()));
        float width = image.getWidth() * scale;
        float height = image.getHeight() * scale;
        content.drawImage(image, x, top - height, width, height);
        return height;
    }

    /** Draws what follows (text and rules) in the #RRGGBB colour, or black when null. */
    void color(String hex) throws IOException {
        float[] rgb = hex == null ? new float[] {0f, 0f, 0f} : new float[] {Integer.parseInt(hex.substring(1, 3), 16) / 255f,
            Integer.parseInt(hex.substring(3, 5), 16) / 255f, Integer.parseInt(hex.substring(5, 7), 16) / 255f};
        content.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
        content.setStrokingColor(rgb[0], rgb[1], rgb[2]);
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
        stampFooters();
    }

    /** Whether a thin rule is drawn above the footer of every page. */
    private boolean footerRule;

    void footerRule(boolean drawn) {
        this.footerRule = drawn;
    }

    /** The page's height and full width, for what is drawn edge to edge. */
    float pageHeight() {
        return pageSize.getHeight();
    }

    float pageWidth() {
        return pageSize.getWidth();
    }

    /** A rectangle filled with the #RRGGBB colour, the colour back to black after it. */
    void fill(float x, float y, float boxWidth, float height, String hex) throws IOException {
        color(hex);
        content.addRect(x, y, boxWidth, height);
        content.fill();
        color(null);
    }

    /** Draws what follows in white, as on a filled band. */
    void white() throws IOException {
        content.setNonStrokingColor(1f, 1f, 1f);
        content.setStrokingColor(1f, 1f, 1f);
    }

    /** Writes this text at the foot of every page, small, on the left, the page numbers on the right. */
    void footer(String text) {
        zones(new Zones(text, null, null, null, null));
    }

    /**
     * What is written small on every page: the footer's left, centre and right zones, and on the pages after the first
     * a header's left and right zones; {@code {{page}}} and {@code {{pages}}} in them become the page's number and the
     * count. Each is null for none; with no right footer, a document of several pages shows "1/2" there.
     */
    record Zones(String footerLeft, String footerCenter, String footerRight, String headerLeft, String headerRight) {

        Zones {
            footerLeft = clean(footerLeft);
            footerCenter = clean(footerCenter);
            footerRight = clean(footerRight);
            headerLeft = clean(headerLeft);
            headerRight = clean(headerRight);
        }

        private static String clean(String text) {
            return text == null || text.isBlank() ? null : text.strip().replaceAll("\\s+", " ");
        }

        boolean empty() {
            return footerLeft == null && footerCenter == null && footerRight == null && headerLeft == null && headerRight == null;
        }

        static String paged(String text, int page, int pages) {
            return text == null ? null : text.replace("{{page}}", String.valueOf(page)).replace("{{pages}}", String.valueOf(pages));
        }
    }

    void zones(Zones given) {
        this.zones = given == null ? new Zones(null, null, null, null, null) : given;
    }

    /** The zones under the bottom margin of every page and above the top one of the following pages. */
    private void stampFooters() throws IOException {
        int pages = document.getNumberOfPages();
        if (zones.empty() && pages < 2 && !footerRule) {
            return;
        }
        float size = ZONE_SIZE;
        float baseline = keptBottom - MARGIN / 2;
        float top = pageSize.getHeight() - keptTop + MARGIN / 2;
        float third = (pageSize.getWidth() - 2 * MARGIN) / 3 - 6;
        for (int index = 0; index < pages; index++) {
            try (PDPageContentStream stamp = new PDPageContentStream(document, document.getPage(index),
                PDPageContentStream.AppendMode.APPEND, true, true)) {
                stamp.setNonStrokingColor(0.35f, 0.35f, 0.35f);
                if (footerRule) {
                    stamp.setStrokingColor(0.35f, 0.35f, 0.35f);
                    stamp.setLineWidth(0.4f);
                    stamp.moveTo(MARGIN, baseline + size + 4);
                    stamp.lineTo(pageSize.getWidth() - MARGIN, baseline + size + 4);
                    stamp.stroke();
                }
                boolean thirds = zones.footerCenter() != null || zones.footerRight() != null;
                String right = zones.footerRight() != null ? zones.footerRight() : pages > 1 ? "{{page}}/{{pages}}" : null;
                float leftWidth = thirds ? third : pageSize.getWidth() - 2 * MARGIN - 40;
                stampRow(stamp, baseline, Zones.paged(zones.footerLeft(), index + 1, pages), leftWidth,
                    Zones.paged(zones.footerCenter(), index + 1, pages), Zones.paged(right, index + 1, pages), third);
                if (index > 0) {
                    stampRow(stamp, top, Zones.paged(zones.headerLeft(), index + 1, pages), pageSize.getWidth() / 2 - MARGIN - 6, null,
                        Zones.paged(zones.headerRight(), index + 1, pages), pageSize.getWidth() / 2 - MARGIN - 6);
                }
            }
        }
    }

    /** A row of zones: on the left, centred and on the right, each cut to its width. */
    private void stampRow(PDPageContentStream stamp, float baseline, String left, float leftWidth, String center, String right,
                          float width) throws IOException {
        float size = ZONE_SIZE;
        if (left != null) {
            writeAt(stamp, MARGIN, baseline, ellipsize(left, false, size, leftWidth), size);
        }
        if (center != null) {
            String text = ellipsize(center, false, size, width);
            writeAt(stamp, (pageSize.getWidth() - textWidth(text, false, size)) / 2, baseline, text, size);
        }
        if (right != null) {
            String text = ellipsize(right, false, size, width);
            writeAt(stamp, pageSize.getWidth() - MARGIN - textWidth(text, false, size), baseline, text, size);
        }
    }

    private void writeAt(PDPageContentStream stamp, float x, float baseline, String text, float size) throws IOException {
        String drawable = sanitize(text, false);
        if (drawable.isEmpty()) {
            return;
        }
        stamp.beginText();
        stamp.setFont(regular, size);
        stamp.newLineAtOffset(x, baseline);
        stamp.showText(drawable);
        stamp.endText();
    }

    /** Goes on to a new page, whatever room is left. */
    void startPage() throws IOException {
        newPage();
    }

    private void newPage() throws IOException {
        if (content != null) {
            content.close();
        }
        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        cursor = pageSize.getHeight() - keptTop;
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
