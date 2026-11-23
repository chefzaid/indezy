package dev.swirlit.indezy.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A fillable PDF form as an invoice or quote template (see docs/features.md#invoices): its fields named after the
 * placeholders ({@code invoice.number}, {@code client.name}, {@code totals.toPay}: a field {@code number} inside a field
 * {@code invoice}, or a flat {@code invoice_number}), a line's as {@code line.description.1},
 * {@code line.amount.1} for the first row, {@code .2} for the second, and a check box {@code vatExempt}. Indezy fills
 * them in its own font and flattens the form; lines past the form's rows, and the mentions it has no field for, go on a
 * page added at the end. A copy for the freelancer, as the Word one: the Factur-X invoice stays the built-in PDF.
 */
public final class PdfFormTemplate {

    private static final Pattern ROW = Pattern.compile("(line\\.[A-Za-z]+)\\.(\\d{1,3})");
    private static final Pattern FONT_SIZE = Pattern.compile("([\\d.]+)\\s+Tf");
    private static final COSName FONT = COSName.getPDFName("IndezyF");
    private static final float MARGIN = 50;
    private static final float SIZE = 10;
    private static final float LEADING = 14;

    private PdfFormTemplate() {
    }

    /** Whether the bytes are a PDF rather than a Word document. */
    public static boolean isPdf(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    /** Reads the form; refused when it is encrypted, scripted, or has no field. */
    public static DocxTemplate.Analysis analyse(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("This PDF is protected: save it without a password");
            }
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            if (form == null || !form.getFieldIterator().hasNext()) {
                throw new IllegalArgumentException("This PDF has no form field: name its fields after the placeholders, such as invoice.number");
            }
            if (document.getDocumentCatalog().getNames() != null && document.getDocumentCatalog().getNames().getJavaScript() != null) {
                throw new IllegalArgumentException("This PDF holds scripts: save it without them");
            }
            Set<String> found = new LinkedHashSet<>();
            Set<String> unknown = new LinkedHashSet<>();
            for (PDField field : form.getFieldTree()) {
                if (field instanceof PDTerminalField) {
                    String key = key(field.getFullyQualifiedName());
                    (key != null ? found : unknown).add(field.getFullyQualifiedName());
                }
            }
            Set<String> canonical = new LinkedHashSet<>();
            found.forEach(name -> canonical.add(key(name)));
            if (canonical.stream().anyMatch(key -> key.startsWith("line."))) {
                canonical.add("#lines");
            }
            List<String> missing = DocxTemplate.REQUIRED.entrySet().stream().filter(mention -> mention.getValue().stream().noneMatch(canonical::contains))
                .map(Map.Entry::getKey).toList();
            return new DocxTemplate.Analysis(List.copyOf(found), List.copyOf(unknown), missing);
        } catch (IOException e) {
            throw new IllegalArgumentException("This file is not a PDF that can be read", e);
        }
    }

    /** The form filled for one invoice or quote, flattened. */
    static byte[] fill(byte[] pdf, BillingPage page) {
        return fill(pdf, DocxTemplate.values(page, null));
    }

    static byte[] fill(byte[] pdf, DocxTemplate.Values values) {
        DocxTemplate.Analysis analysis = analyse(pdf);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDAcroForm form = document.getDocumentCatalog().getAcroForm();
            PDFont font = PDType0Font.load(document, new ByteArrayInputStream(PdfFonts.read(PdfFonts.REGULAR)), false);
            PDResources resources = form.getDefaultResources() != null ? form.getDefaultResources() : new PDResources();
            resources.put(FONT, font);
            form.setDefaultResources(resources);
            form.setNeedAppearances(false);
            int rows = 0;
            for (PDField field : form.getFieldTree()) {
                String key = key(field.getFullyQualifiedName());
                if (key == null) {
                    continue;
                }
                if (field instanceof PDCheckBox box && "vatExempt".equals(key)) {
                    if (Boolean.TRUE.equals(values.cases().get("vatExempt"))) {
                        box.check();
                    } else {
                        box.unCheck();
                    }
                } else if (field instanceof PDTextField text) {
                    text.setDefaultAppearance("/IndezyF " + size(text.getDefaultAppearance()) + " Tf 0 g");
                    if (key.startsWith("line.")) {
                        int row = row(field.getFullyQualifiedName());
                        rows = Math.max(rows, row);
                        text.setValue(row <= values.lines().size() ? values.lines().get(row - 1).getOrDefault(key, "") : "");
                    } else {
                        text.setValue(values.fields().getOrDefault(key, ""));
                    }
                }
            }
            form.flatten();
            List<String> extra = extra(values, analysis.missing(), rows);
            if (!extra.isEmpty()) {
                addPage(document, font, extra);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not fill the PDF form", e);
        }
    }

    /** What the form has no room for: the lines past its rows, and the mentions it lacks. */
    private static List<String> extra(DocxTemplate.Values values, List<String> missing, int rows) {
        List<String> lines = new ArrayList<>();
        if (values.lines().size() > rows) {
            for (Map<String, String> line : values.lines().subList(rows, values.lines().size())) {
                lines.add(line.get("line.description") + " — " + line.get("line.quantity") + " × " + line.get("line.unitPrice") + " = " +
                    line.get("line.amount"));
            }
        }
        if (missing.contains("seller")) {
            lines.addAll(values.fields().getOrDefault("seller.details", "").lines().toList());
        }
        if (missing.contains("client")) {
            lines.addAll(values.fields().getOrDefault("client.details", "").lines().toList());
        }
        if (missing.contains("totals")) {
            lines.add(values.fields().getOrDefault("totals.toPay", ""));
        }
        if (missing.contains("mentions")) {
            lines.addAll(values.fields().getOrDefault("mentions", "").lines().toList());
        }
        if (missing.contains("number") || missing.contains("date")) {
            lines.addFirst(values.fields().getOrDefault("document.title", "") + " " + values.fields().getOrDefault("document.number", "") + " — " +
                values.fields().getOrDefault("document.issueDate", ""));
        }
        return lines.stream().filter(line -> !line.isBlank()).toList();
    }

    private static void addPage(PDDocument document, PDFont font, List<String> lines) throws IOException {
        PDPageContentStream content = null;
        float y = 0;
        try {
            for (String line : lines) {
                if (content == null || y < MARGIN) {
                    if (content != null) {
                        content.close();
                    }
                    PDPage page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = PDRectangle.A4.getHeight() - MARGIN;
                }
                content.beginText();
                content.setFont(font, SIZE);
                content.newLineAtOffset(MARGIN, y);
                content.showText(printable(font, line));
                content.endText();
                y -= LEADING;
            }
        } finally {
            if (content != null) {
                content.close();
            }
        }
    }

    /** The line with the characters the font cannot draw left out. */
    private static String printable(PDFont font, String line) {
        StringBuilder kept = new StringBuilder();
        line.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            try {
                font.encode(character);
                kept.append(character);
            } catch (IOException | IllegalArgumentException e) {
                kept.append(' ');
            }
        });
        return kept.toString();
    }

    /** The placeholder a field is named after ({@code invoice.} and {@code quote.} as {@code document.}, a row's number aside); null for none. */
    static String key(String name) {
        if (name == null) {
            return null;
        }
        // Dots make a hierarchy of fields (invoice > number); an underscore stands for one in a flat name.
        String trimmed = name.strip().replace('_', '.');
        Matcher row = ROW.matcher(trimmed);
        String base = row.matches() ? row.group(1) : trimmed;
        if ("vatExempt".equals(base)) {
            return base;
        }
        String canonical = DocxTemplate.canonical(base);
        boolean line = canonical.startsWith("line.");
        if (line != row.matches() || !DocxTemplate.PLACEHOLDERS.contains(canonical) || canonical.equals("logo")) {
            return null;
        }
        return canonical;
    }

    private static int row(String name) {
        Matcher row = ROW.matcher(name.strip().replace('_', '.'));
        return row.matches() ? Integer.parseInt(row.group(2)) : 0;
    }

    /** The field's font size, 0 (fit to the box) when it names none. */
    private static String size(String appearance) {
        Matcher size = appearance == null ? null : FONT_SIZE.matcher(appearance);
        return size != null && size.find() ? size.group(1) : "0";
    }
}
