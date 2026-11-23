package dev.swirlit.indezy.pdf;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A Word document ({@code .docx}) written as its few XML parts, without a library: paragraphs, tables, one picture, a
 * footer with the page number, on A4 with the PDF's margins. Enough for an invoice or a quote laid out like its PDF.
 */
final class DocxDocument {

    /** Half-points, as Word measures letters. */
    static final int BODY = 19;
    static final int SMALL = 16;
    static final int TITLE = 40;
    static final int NAME = 22;
    /** Twentieths of a point, as Word measures the page: A4 and its usable width between 2 cm margins. */
    static final int PAGE_WIDTH = 11_906;
    static final int PAGE_HEIGHT = 16_838;
    static final int MARGIN = 1_134;
    static final int CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final int EMU_PER_POINT = 12_700;

    enum Align { LEFT, RIGHT, CENTER }

    /** A run of text: its words, bold or not, its size in half-points and its colour as {@code RRGGBB}, or none. */
    record Run(String text, boolean bold, int size, String color) {
    }

    /** A table cell: its paragraphs' runs, one list per paragraph, aligned, filled with a colour as {@code RRGGBB} or not. */
    record Cell(List<List<Run>> paragraphs, Align align, String fill) {

        Cell {
            paragraphs = List.copyOf(paragraphs.stream().map(List::copyOf).toList());
        }

        Cell(List<List<Run>> paragraphs, Align align) {
            this(paragraphs, align, null);
        }
    }

    private final StringBuilder body = new StringBuilder();
    private final String font;
    private final String footer;
    private byte[] picture;
    private String pictureType;

    /** {@code font}: the family the document is written in; {@code footer}: the text at the bottom of each page, or none. */
    DocxDocument(String font, String footer) {
        this.font = font;
        this.footer = footer == null || footer.isBlank() ? null : footer.strip();
    }

    void paragraph(List<Run> runs, Align align, int spaceAfter) {
        body.append(paragraphXml(runs, align, spaceAfter));
    }

    void paragraph(String text, boolean bold, int size, Align align) {
        paragraph(List.of(new Run(text, bold, size, null)), align, 60);
    }

    /** A thin line across the page, in the colour given as {@code RRGGBB} or grey. */
    void rule(String color) {
        body.append("<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"")
            .append(color == null ? "808080" : color).append("\"/></w:pBdr><w:spacing w:after=\"120\"/></w:pPr></w:p>");
    }

    /** A page break: what follows starts a new page. */
    void pageBreak() {
        body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>");
    }

    /**
     * A table: its columns' widths in twentieths of a point, its rows of cells; {@code header} draws the first row in
     * bold with a line under it in the {@code color} given, and {@code ruled} a line under the last row; {@code boxed}
     * draws a border around each cell.
     */
    void table(int[] widths, List<List<Cell>> rows, boolean header, boolean ruled, boolean boxed, String color) {
        String line = color == null ? "000000" : color;
        body.append("<w:tbl><w:tblPr><w:tblW w:w=\"").append(sum(widths)).append("\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/>");
        if (boxed) {
            body.append("<w:tblBorders>");
            for (String side : List.of("top", "left", "bottom", "right")) {
                body.append("<w:").append(side).append(" w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"808080\"/>");
            }
            body.append("</w:tblBorders>");
        }
        body.append("<w:tblCellMar><w:left w:w=\"0\" w:type=\"dxa\"/><w:right w:w=\"80\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr><w:tblGrid>");
        for (int width : widths) {
            body.append("<w:gridCol w:w=\"").append(width).append("\"/>");
        }
        body.append("</w:tblGrid>");
        for (int index = 0; index < rows.size(); index++) {
            boolean first = index == 0 && header;
            boolean last = index == rows.size() - 1 && ruled;
            body.append("<w:tr>");
            List<Cell> cells = rows.get(index);
            for (int column = 0; column < cells.size(); column++) {
                body.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(widths[column]).append("\" w:type=\"dxa\"/>");
                if (first || last) {
                    body.append("<w:tcBorders><w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"").append(line).append("\"/></w:tcBorders>");
                }
                Cell cell = cells.get(column);
                if (cell.fill() != null) {
                    body.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"").append(cell.fill()).append("\"/>");
                }
                body.append("</w:tcPr>");
                if (cell.paragraphs().isEmpty()) {
                    body.append("<w:p/>");
                }
                for (List<Run> paragraph : cell.paragraphs()) {
                    body.append(paragraphXml(paragraph, cell.align(), 20));
                }
                body.append("</w:tc>");
            }
            body.append("</w:tr>");
        }
        body.append("</w:tbl>");
    }

    /** The picture, PNG or JPEG, fitted in a box of {@code width} by {@code height} points; nothing when it cannot be read. */
    void picture(byte[] image, float width, float height, Align align) {
        BufferedImage read;
        try {
            read = ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException e) {
            read = null;
        }
        if (read == null || picture != null) {
            return;
        }
        double scale = Math.min(width / read.getWidth(), height / read.getHeight());
        long cx = Math.round(read.getWidth() * scale * EMU_PER_POINT);
        long cy = Math.round(read.getHeight() * scale * EMU_PER_POINT);
        picture = image.clone();
        pictureType = image.length > 3 && (image[0] & 0xFF) == 0x89 && image[1] == 'P' ? "png" : "jpeg";
        body.append("<w:p><w:pPr><w:spacing w:after=\"120\"/><w:jc w:val=\"").append(jc(align)).append("\"/></w:pPr><w:r><w:drawing>")
            .append("<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"").append(cx).append("\" cy=\"").append(cy)
            .append("\"/><wp:docPr id=\"1\" name=\"Logo\"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>")
            .append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic><pic:nvPicPr>")
            .append("<pic:cNvPr id=\"1\" name=\"logo\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"rIdLogo\"/>")
            .append("<a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(cx)
            .append("\" cy=\"").append(cy).append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>")
            .append("</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>");
    }

    /** The document as a {@code .docx} file. */
    byte[] save(String title, String author) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"" + RELATIONSHIPS + "\">" +
                "<Relationship Id=\"rId1\" Type=\"" + OFFICE + "/officeDocument\" Target=\"word/document.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" " +
                "Target=\"docProps/core.xml\"/></Relationships>");
            put(zip, "docProps/core.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><cp:coreProperties " +
                "xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
                "<dc:title>" + escape(title) + "</dc:title><dc:creator>" + escape(author) + "</dc:creator></cp:coreProperties>");
            put(zip, "word/_rels/document.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"" +
                RELATIONSHIPS + "\"><Relationship Id=\"rIdStyles\" Type=\"" + OFFICE + "/styles\" Target=\"styles.xml\"/>" +
                "<Relationship Id=\"rIdFooter\" Type=\"" + OFFICE + "/footer\" Target=\"footer1.xml\"/>" +
                (picture == null ? "" : "<Relationship Id=\"rIdLogo\" Type=\"" + OFFICE + "/image\" Target=\"media/logo." + extension() + "\"/>") +
                "</Relationships>");
            put(zip, "word/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:styles xmlns:w=\"" + WORD + "\">" +
                "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"" + escape(font) + "\" w:hAnsi=\"" + escape(font) + "\" w:cs=\"" +
                escape(font) + "\"/><w:sz w:val=\"" + BODY + "\"/><w:lang w:val=\"fr-FR\"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr>" +
                "<w:spacing w:after=\"40\" w:line=\"252\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults></w:styles>");
            put(zip, "word/footer1.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:ftr xmlns:w=\"" + WORD + "\">" +
                "<w:p><w:pPr><w:tabs><w:tab w:val=\"right\" w:pos=\"" + CONTENT_WIDTH + "\"/></w:tabs></w:pPr>" +
                (footer == null ? "" : run(new Run(footer, false, SMALL - 2, "666666"))) +
                "<w:r><w:rPr><w:sz w:val=\"" + (SMALL - 2) + "\"/></w:rPr><w:tab/></w:r><w:fldSimple w:instr=\"PAGE\"><w:r><w:rPr>" +
                "<w:sz w:val=\"" + (SMALL - 2) + "\"/></w:rPr><w:t>1</w:t></w:r></w:fldSimple></w:p></w:ftr>");
            put(zip, "word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"" + WORD +
                "\" xmlns:r=\"" + OFFICE + "\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
                "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                "<w:body>" + body + "<w:sectPr><w:footerReference w:type=\"default\" r:id=\"rIdFooter\"/><w:pgSz w:w=\"" + PAGE_WIDTH +
                "\" w:h=\"" + PAGE_HEIGHT + "\"/><w:pgMar w:top=\"" + MARGIN + "\" w:right=\"" + MARGIN + "\" w:bottom=\"" + MARGIN +
                "\" w:left=\"" + MARGIN + "\" w:header=\"567\" w:footer=\"567\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>");
            if (picture != null) {
                zip.putNextEntry(new ZipEntry("word/media/logo." + extension()));
                zip.write(picture);
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the Word document", e);
        }
        return bytes.toByteArray();
    }

    private static final String WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String OFFICE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships";

    private String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            (picture == null ? "" : "<Default Extension=\"" + extension() + "\" ContentType=\"image/" + pictureType + "\"/>") +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
            "<Override PartName=\"/word/footer1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\"/>" +
            "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/></Types>";
    }

    private String extension() {
        return "png".equals(pictureType) ? "png" : "jpeg";
    }

    private static String paragraphXml(List<Run> runs, Align align, int spaceAfter) {
        StringBuilder xml = new StringBuilder("<w:p><w:pPr><w:spacing w:after=\"").append(spaceAfter).append("\"/><w:jc w:val=\"")
            .append(jc(align)).append("\"/></w:pPr>");
        for (Run run : runs) {
            xml.append(run(run));
        }
        return xml.append("</w:p>").toString();
    }

    /** A run, its line breaks kept. */
    private static String run(Run run) {
        StringBuilder xml = new StringBuilder("<w:r><w:rPr>");
        if (run.bold()) {
            xml.append("<w:b/>");
        }
        if (run.color() != null) {
            xml.append("<w:color w:val=\"").append(run.color()).append("\"/>");
        }
        xml.append("<w:sz w:val=\"").append(run.size()).append("\"/></w:rPr>");
        String[] lines = (run.text() == null ? "" : run.text()).split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                xml.append("<w:br/>");
            }
            xml.append("<w:t xml:space=\"preserve\">").append(escape(lines[index])).append("</w:t>");
        }
        return xml.append("</w:r>").toString();
    }

    private static String jc(Align align) {
        return switch (align) {
            case LEFT -> "left";
            case RIGHT -> "right";
            case CENTER -> "center";
        };
    }

    /** The text as XML, without the characters XML cannot hold. */
    static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(text.length());
        text.codePoints().forEach(point -> {
            switch (point) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                default -> {
                    if (point == '\t' || point >= 0x20 && point != 0xFFFE && point != 0xFFFF) {
                        escaped.appendCodePoint(point);
                    }
                }
            }
        });
        return escaped.toString();
    }

    private static int sum(int[] widths) {
        int total = 0;
        for (int width : widths) {
            total += width;
        }
        return total;
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
