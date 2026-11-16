package dev.swirlit.indezy.media;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

/**
 * The images of the network's posts and messages, made anew from their pixels (see docs/security.md#member-network):
 * turned upright as the camera said, at most 2048 pixels on their longest side, written again as JPEG (PNG when they
 * have transparent parts), so that none of what the file carried beside its pixels (place, camera, date) is kept.
 */
public final class NetworkImages {

    public static final int LONGEST_SIDE = 2048;
    /** The most pixels an image may have before it is read: 50 million, beyond any camera's. */
    static final long MAX_PIXELS = 50_000_000L;
    /** A thumbnail of a document's first page, this wide. */
    public static final int PREVIEW_WIDTH = 800;
    private static final float QUALITY = 0.85f;
    private static final String UNREADABLE = "This image cannot be read: send a PNG, a JPEG or a GIF";

    /** An image refused, with the reason the member reads (400). */
    static final class Refused extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        Refused(String reason, Throwable cause) {
            super(reason, cause);
        }
    }

    /** An image made anew: its bytes, type and size. */
    public record Image(byte[] content, String contentType, int width, int height) {

        public Image {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    private NetworkImages() {
    }

    /** Reads the image, refused (400) when it cannot be read or is too large to. */
    public static Image process(byte[] content) {
        BufferedImage read = read(content);
        boolean jpeg = content.length > 2 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8;
        int orientation = jpeg ? ExifOrientation.of(content) : ExifOrientation.NORMAL;
        return write(upright(read, orientation), LONGEST_SIDE);
    }

    /** Writes a picture at most {@code longest} pixels on its longest side: JPEG, or PNG with transparent parts. */
    public static Image write(BufferedImage picture, int longest) {
        double scale = Math.min(1.0, (double) longest / Math.max(picture.getWidth(), picture.getHeight()));
        int width = Math.max(1, (int) Math.round(picture.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(picture.getHeight() * scale));
        boolean alpha = picture.getColorModel().hasAlpha();
        BufferedImage drawn = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = drawn.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (!alpha) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
            }
            graphics.drawImage(picture, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        try {
            return alpha ? new Image(png(drawn), "image/png", width, height) : new Image(jpeg(drawn), "image/jpeg", width, height);
        } catch (IOException e) {
            throw new IllegalStateException("The image could not be written", e);
        }
    }

    private static BufferedImage read(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = input == null ? Collections.emptyIterator() : ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new Refused(UNREADABLE, null);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw new Refused("This image is too large: 50 million pixels at most", null);
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (Refused refused) {
            throw refused;
        } catch (IOException | RuntimeException e) {
            throw new Refused(UNREADABLE, e);
        }
    }

    /** The picture turned and mirrored as the EXIF orientation says. */
    static BufferedImage upright(BufferedImage picture, int orientation) {
        if (orientation <= ExifOrientation.NORMAL || orientation > 8) {
            return picture;
        }
        int w = picture.getWidth();
        int h = picture.getHeight();
        boolean swap = orientation >= 5;
        // Where each pixel goes, as AffineTransform(m00, m10, m01, m11, m02, m12): x' = m00 x + m01 y + m02, y' = m10 x + m11 y + m12.
        AffineTransform turn = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
        BufferedImage turned = new BufferedImage(swap ? h : w, swap ? w : h,
            picture.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = turned.createGraphics();
        try {
            graphics.drawImage(picture, turn, null);
        } finally {
            graphics.dispose();
        }
        return turned;
    }

    private static byte[] jpeg(BufferedImage picture) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(picture, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static byte[] png(BufferedImage picture) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(picture, "png", bytes);
        return bytes.toByteArray();
    }
}
