package dev.swirlit.indezy.util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * The files an opportunity accepts, recognised from their content rather than from what the browser declares: PDF,
 * PNG, JPEG, GIF and WebP images (shown in the page), Word and OpenDocument texts and plain text (downloaded).
 */
public final class AttachmentTypes {

    /** A recognised type, and whether the browser may show it in the page. */
    public record Type(String contentType, boolean inline) {
    }

    private static final Type PDF = new Type("application/pdf", true);
    private static final Type PNG = new Type("image/png", true);
    private static final Type JPEG = new Type("image/jpeg", true);
    private static final Type GIF = new Type("image/gif", true);
    private static final Type WEBP = new Type("image/webp", true);
    private static final Type DOCX = new Type("application/vnd.openxmlformats-officedocument.wordprocessingml.document", false);
    private static final Type ODT = new Type("application/vnd.oasis.opendocument.text", false);
    private static final Type TEXT = new Type("text/plain; charset=UTF-8", false);
    private static final int WEBP_TAG_OFFSET = 8;

    private AttachmentTypes() {
    }

    /** The type of a file named {@code fileName} holding {@code content}, or empty when it is not accepted. */
    public static Optional<Type> detect(String fileName, byte[] content) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (startsWith(content, 0, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return Optional.of(PDF);
        }
        if (startsWith(content, 0, new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'})) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, "GIF87a".getBytes(StandardCharsets.US_ASCII)) ||
            startsWith(content, 0, "GIF89a".getBytes(StandardCharsets.US_ASCII))) {
            return Optional.of(GIF);
        }
        if (startsWith(content, 0, "RIFF".getBytes(StandardCharsets.US_ASCII)) &&
            startsWith(content, WEBP_TAG_OFFSET, "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return Optional.of(WEBP);
        }
        // Word and OpenDocument files are zip archives: the name tells which, the content that it is one.
        if (startsWith(content, 0, new byte[] {'P', 'K', 3, 4})) {
            return name.endsWith(".docx") ? Optional.of(DOCX) : name.endsWith(".odt") ? Optional.of(ODT) : Optional.empty();
        }
        if ((name.endsWith(".txt") || name.endsWith(".md")) && isText(content)) {
            return Optional.of(TEXT);
        }
        return Optional.empty();
    }

    /** Whether a stored type is one the browser may show in the page: PDF and the accepted images. */
    public static boolean isInline(String contentType) {
        return contentType.equals(PDF.contentType()) || contentType.startsWith("image/");
    }

    private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        if (content.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Valid UTF-8 without NUL bytes. */
    private static boolean isText(byte[] content) {
        for (byte b : content) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
