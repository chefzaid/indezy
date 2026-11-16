package dev.swirlit.indezy.media;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Images, PDFs and Office archives made for the tests of the network's files. */
public final class NetworkFixtures {

    private NetworkFixtures() {
    }

    /** An image of that size and format (png keeps a transparent corner), its left half red and its right half blue. */
    public static byte[] image(int width, int height, String format) throws IOException {
        boolean png = format.equals("png");
        BufferedImage picture = new BufferedImage(width, height, png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = picture.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width / 2, height);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(width / 2, 0, width - width / 2, height);
        graphics.dispose();
        if (png) {
            picture.setRGB(0, 0, 0);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(picture, format, bytes);
        return bytes.toByteArray();
    }

    /** The JPEG with an EXIF block saying how to turn it (orientation 1 to 8), as a phone writes it. */
    public static byte[] withOrientation(byte[] jpeg, int orientation, boolean littleEndian) {
        byte[] little = {'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0, 0, 0, 0, 0};
        byte[] big = {'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0, 0, 0, 0, 0};
        byte[] tiff = littleEndian ? little : big;
        byte[] header = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int length = 2 + header.length + tiff.length;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(jpeg, 0, 2);
        bytes.write(0xFF);
        bytes.write(0xE1);
        bytes.write(length >> 8);
        bytes.write(length & 0xFF);
        bytes.writeBytes(header);
        bytes.writeBytes(tiff);
        bytes.write(jpeg, 2, jpeg.length - 2);
        return bytes.toByteArray();
    }

    /** A PDF of that many pages, its first holding the text. */
    public static byte[] pdf(String text, int pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (i == 0) {
                    try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                        stream.beginText();
                        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                        stream.newLineAtOffset(72, 700);
                        stream.showText(text);
                        stream.endText();
                    }
                }
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    /** A zip archive of those entries, as Office and OpenDocument files are. */
    public static byte[] archive(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
