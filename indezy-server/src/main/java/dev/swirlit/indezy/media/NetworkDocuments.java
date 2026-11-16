package dev.swirlit.indezy.media;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The documents of the network's posts and messages (see docs/security.md#member-network): PDF, slides and spreadsheets
 * (and text documents), their type read from their content, never from what the browser says; Office files with macros
 * are refused, as are the older binary formats; a PDF's first page is drawn for its card; the text of each is read for
 * the confidentiality check before it goes out.
 */
public final class NetworkDocuments {

    /** A document's type as served, and the extension its name keeps. */
    public record Type(String contentType, String extension) {
    }

    /** A PDF's first page drawn, and its number of pages. */
    public record Preview(byte[] png, int pages) {

        public Preview {
            png = png.clone();
        }

        @Override
        public byte[] png() {
            return png.clone();
        }
    }

    static final Type PDF = new Type("application/pdf", "pdf");
    private static final Map<String, Type> ARCHIVES = Map.of(
        "docx", new Type("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
        "xlsx", new Type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
        "pptx", new Type("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
        "odt", new Type("application/vnd.oasis.opendocument.text", "odt"),
        "ods", new Type("application/vnd.oasis.opendocument.spreadsheet", "ods"),
        "odp", new Type("application/vnd.oasis.opendocument.presentation", "odp"));
    private static final List<String> MACRO_EXTENSIONS = List.of("docm", "xlsm", "pptm", "dotm", "xltm", "potm", "ppsm", "xlsb");
    /** Read at most this much of an archive, entries this many, so that a small file cannot unpack into a huge one. */
    static final long MAX_UNPACKED = 30L * 1024 * 1024;
    static final int MAX_ENTRIES = 5_000;
    static final int TEXT_MAX = 50_000;
    private static final int TEXT_PAGES = 30;
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final String REFUSED = "Send a PDF, slides or a spreadsheet (pptx, xlsx, docx, or their OpenDocument forms)";

    private NetworkDocuments() {
    }

    /** The document's type, refused (400) with the reason when it is not one the network takes or holds macros. */
    public static Type detect(String fileName, byte[] content) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        if (startsWith(content, "%PDF-")) {
            return PDF;
        }
        if (MACRO_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Files with macros are not shared: save it without them (as " +
                extension.substring(0, extension.length() - 1) + "x)");
        }
        if (content.length >= 4 && content[0] == 'P' && content[1] == 'K' && content[2] == 3 && content[3] == 4) {
            Type type = ARCHIVES.get(extension);
            if (type == null) {
                throw new IllegalArgumentException(REFUSED);
            }
            if (hasMacros(content)) {
                throw new IllegalArgumentException("Files with macros are not shared: save it without them");
            }
            return type;
        }
        throw new IllegalArgumentException(REFUSED);
    }

    /** The document's text, at most 50,000 characters; empty when it cannot be read. */
    public static String text(Type type, byte[] content) {
        try {
            String text = type.equals(PDF) ? pdfText(content) : archiveText(type, content);
            text = SPACES.matcher(text).replaceAll(" ").strip();
            return text.length() > TEXT_MAX ? text.substring(0, TEXT_MAX) : text;
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** A PDF's first page drawn 800 pixels wide, and its pages; none for another type or a PDF that cannot be read. */
    public static Optional<Preview> preview(Type type, byte[] content) {
        if (!type.equals(PDF)) {
            return Optional.empty();
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.getNumberOfPages() == 0) {
                return Optional.empty();
            }
            float width = document.getPage(0).getMediaBox().getWidth();
            float scale = width <= 0 ? 1 : Math.min(4f, NetworkImages.PREVIEW_WIDTH / width);
            BufferedImage page = new PDFRenderer(document).renderImage(0, scale, ImageType.RGB);
            NetworkImages.Image drawn = NetworkImages.write(page, NetworkImages.PREVIEW_WIDTH * 2);
            return Optional.of(new Preview(drawn.content(), document.getNumberOfPages()));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String pdfText(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(TEXT_PAGES);
            return stripper.getText(document);
        }
    }

    /** The text of an Office or OpenDocument file: its words, slides, shared strings or content, tags dropped. */
    private static String archiveText(Type type, byte[] content) throws IOException {
        StringBuilder text = new StringBuilder();
        walk(content, (name, entry) -> {
            if (isTextPart(type, name)) {
                text.append(' ').append(unescape(TAG.matcher(new String(entry.readAllBytes(), StandardCharsets.UTF_8)).replaceAll(" ")));
            }
            return text.length() < TEXT_MAX;
        });
        return text.toString();
    }

    private static boolean isTextPart(Type type, String name) {
        return switch (type.extension()) {
            case "docx" -> name.equals("word/document.xml");
            case "pptx" -> name.startsWith("ppt/slides/slide") && name.endsWith(".xml");
            case "xlsx" -> name.equals("xl/sharedStrings.xml");
            default -> name.equals("content.xml");
        };
    }

    /** Whether the archive carries macros; refused when it holds no entry it can read. */
    private static boolean hasMacros(byte[] content) {
        boolean[] found = {false};
        int[] entries = {0};
        try {
            walk(content, (name, entry) -> {
                entries[0]++;
                String lower = name.toLowerCase(Locale.ROOT);
                found[0] = lower.endsWith("vbaproject.bin") || lower.startsWith("basic/") || lower.startsWith("scripts/") ||
                    lower.contains("/vbadata.xml");
                return !found[0];
            });
        } catch (IOException e) {
            throw new IllegalArgumentException(REFUSED, e);
        }
        if (entries[0] == 0) {
            throw new IllegalArgumentException(REFUSED);
        }
        return found[0];
    }

    /** What to do with one entry of an archive; false stops the walk. */
    @FunctionalInterface
    private interface EntryVisitor {
        boolean visit(String name, InputStream entry) throws IOException;
    }

    /** Visits the archive's entries, refused beyond 5,000 entries or 30 MB unpacked. */
    private static void walk(byte[] content, EntryVisitor visitor) throws IOException {
        long unpacked = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IllegalArgumentException("This archive holds too many files");
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    unpacked += read;
                    if (unpacked > MAX_UNPACKED) {
                        throw new IllegalArgumentException("This document unpacks to too much");
                    }
                    bytes.write(buffer, 0, read);
                }
                if (!visitor.visit(entry.getName(), new ByteArrayInputStream(bytes.toByteArray()))) {
                    return;
                }
            }
        }
    }

    private static String unescape(String xml) {
        return xml.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    private static boolean startsWith(byte[] content, String prefix) {
        byte[] bytes = prefix.getBytes(StandardCharsets.US_ASCII);
        if (content.length < bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (content[i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }
}
