package dev.swirlit.indezy.media;

import dev.swirlit.indezy.exception.FieldRejectedException;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * The text of a document in Word's old binary format ({@code .doc}, Word 97 to 2003), read with Apache POI's HWPF: a
 * CV saved long ago, which {@link NetworkDocuments} does not take. Only its text is read, never its macros or objects.
 */
public final class WordDocText {

    /** The kind a {@code .doc} is served as. */
    public static final NetworkDocuments.Type TYPE = new NetworkDocuments.Type("application/msword", "doc");
    /** The OLE2 compound file signature every {@code .doc} starts with. */
    private static final byte[] SIGNATURE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private WordDocText() {
    }

    /** Whether the file is named {@code .doc} and starts as one. */
    public static boolean is(String fileName, byte[] content) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".doc") || content.length < SIGNATURE.length) {
            return false;
        }
        for (int index = 0; index < SIGNATURE.length; index++) {
            if (content[index] != SIGNATURE[index]) {
                return false;
            }
        }
        return true;
    }

    /** The document's text, paragraphs on their own lines; refused when it is not a Word document that can be read. */
    public static String text(byte[] content) {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(content));
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText().replace("\r\n", "\n").replace('\r', '\n').strip();
        } catch (IOException | RuntimeException e) {
            throw new FieldRejectedException("file", "This Word document cannot be read: save it as a PDF or a docx");
        }
    }
}
