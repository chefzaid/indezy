package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.InvoiceLine;
import dev.swirlit.indezy.model.enums.LogoSize;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A Word template the freelancer laid out (see docs/features.md#invoices), filled with an invoice or a quote: its
 * placeholders ({@code {{invoice.number}}}, {@code {{client.name}}}, {@code {{totals.toPay}}}, {@code {{logo}}}…) replaced,
 * the part between {@code {{#lines}}} and {@code {{/lines}}} (a table row, or paragraphs) repeated per line, and the
 * mentions French law requires that the template leaves out added at its end. Word splits a typed placeholder across
 * runs; a paragraph holding one is first written as a single run, its first run's formatting kept.
 */
public final class DocxTemplate {

    /** The placeholders known, as they are written, {@code invoice.} and {@code quote.} standing for {@code document.}. */
    public static final List<String> PLACEHOLDERS = List.of("document.title", "document.number", "document.issueDate", "document.dueDate",
        "document.period", "document.validUntil", "document.draft", "seller.name", "seller.details", "seller.address", "seller.siret",
        "seller.vatNumber", "seller.iban", "seller.bic", "client.name", "client.details", "client.address", "client.siren", "client.vatNumber",
        "#lines", "line.description", "line.quantity", "line.unitPrice", "line.vatRate", "line.amount", "/lines", "totals.excludingVat",
        "totals.vat", "totals.toPay", "mentions", "notes", "acceptance", "logo", "#vatExempt", "^vatExempt", "/vatExempt");
    /** The cases a part of the template can be shown in, between {@code {{#case}}} (or {@code {{^case}}} for its opposite) and {@code {{/case}}}. */
    static final List<String> CASES = List.of("vatExempt");
    /** The mentions a document must carry, each satisfied by any of its placeholders. */
    private static final Map<String, List<String>> REQUIRED = requiredMentions();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([#/^]?[A-Za-z]+(?:\\.[A-Za-z]+)?)\\s*}}");
    private static final Pattern PARAGRAPH = Pattern.compile("<w:p[ >].*?</w:p>", Pattern.DOTALL);
    private static final Pattern TEXT = Pattern.compile("(<w:t(?: [^>]*)?>)(.*?)(</w:t>)", Pattern.DOTALL);
    private static final Pattern EMPTY_TEXT = Pattern.compile("<w:t/>");
    private static final Pattern BREAK = Pattern.compile("<w:br\\s*/>");
    private static final Pattern TAB = Pattern.compile("<w:tab\\s*/>");
    private static final String BREAK_MARK = "\u0001br\u0001";
    private static final String TAB_MARK = "\u0001tab\u0001";
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_ENTRIES = 1_000;
    private static final int EMU_PER_POINT = 12_700;
    private static final String DOCUMENT = "word/document.xml";

    private DocxTemplate() {
    }

    /** What an uploaded template holds: the placeholders it uses, those unknown, and the mentions it lacks. */
    public record Analysis(List<String> found, List<String> unknown, List<String> missing) {

        public Analysis {
            found = List.copyOf(found);
            unknown = List.copyOf(unknown);
            missing = List.copyOf(missing);
        }
    }

    /** What a filled template says: the fields by placeholder, the lines, and the mentions French law asks for. */
    record Values(Map<String, String> fields, List<Map<String, String>> lines, byte[] logo, LogoSize logoSize, Map<String, Boolean> cases) {

        Values {
            cases = cases == null ? Map.of() : Map.copyOf(cases);
            fields = Map.copyOf(fields);
            lines = List.copyOf(lines.stream().map(Map::copyOf).toList());
            logo = logo == null ? null : logo.clone();
        }

        @Override
        public byte[] logo() {
            return logo == null ? null : logo.clone();
        }
    }

    /** Reads the template; refused when it is not a Word document or holds no placeholder. */
    public static Analysis analyse(byte[] docx) {
        Map<String, byte[]> parts = parts(docx);
        Set<String> found = new LinkedHashSet<>();
        Set<String> unknown = new LinkedHashSet<>();
        for (String name : textParts(parts)) {
            Matcher placeholder = PLACEHOLDER.matcher(merged(new String(parts.get(name), StandardCharsets.UTF_8)));
            while (placeholder.find()) {
                String key = canonical(placeholder.group(1));
                (PLACEHOLDERS.contains(key) ? found : unknown).add(placeholder.group(1));
            }
        }
        if (found.isEmpty() && unknown.isEmpty()) {
            throw new IllegalArgumentException("This Word document holds no placeholder such as {{invoice.number}}: add them where the values go");
        }
        Set<String> canonical = new LinkedHashSet<>(found.stream().map(DocxTemplate::canonical).toList());
        List<String> missing = REQUIRED.entrySet().stream().filter(mention -> mention.getValue().stream().noneMatch(canonical::contains))
            .map(Map.Entry::getKey).toList();
        return new Analysis(List.copyOf(found), List.copyOf(unknown), missing);
    }

    /** The template filled for one invoice or quote, as a {@code .docx}. */
    static byte[] fill(byte[] docx, BillingPage page, byte[] logo) {
        return fill(docx, values(page, logo));
    }

    static byte[] fill(byte[] docx, Values values) {
        Map<String, byte[]> parts = parts(docx);
        Analysis analysis = analyse(docx);
        boolean logoUsed = false;
        for (String name : textParts(parts)) {
            String xml = merged(new String(parts.get(name), StandardCharsets.UTF_8));
            xml = cases(xml, values.cases());
            xml = repeatLines(xml, values.lines());
            if (values.logo() != null && xml.contains("{{logo}}") && name.equals(DOCUMENT)) {
                xml = xml.replace("{{logo}}", "</w:t></w:r>" + logoRun(values) + "<w:r><w:t xml:space=\"preserve\">");
                logoUsed = true;
            }
            xml = substitute(xml, values.fields());
            if (name.equals(DOCUMENT) && !analysis.missing().isEmpty()) {
                xml = appendMissing(xml, analysis.missing(), values);
            }
            parts.put(name, xml.getBytes(StandardCharsets.UTF_8));
        }
        if (logoUsed) {
            addLogo(parts, values.logo());
        }
        return zip(parts);
    }

    /** The values of a page: its fields, seller and client, lines, totals and mentions. */
    static Values values(BillingPage page, byte[] logo) {
        Map<String, String> fields = new LinkedHashMap<>();
        page.fields().forEach((key, value) -> fields.put("document." + key, value));
        fields.put("seller.name", page.sellerName());
        fields.put("seller.details", String.join("\n", page.sellerLines()));
        fields.put("seller.address", nullToEmpty(page.seller.getAddress()));
        fields.put("seller.siret", nullToEmpty(page.seller.getSiret()));
        fields.put("seller.vatNumber", Boolean.TRUE.equals(page.seller.getVatExempt()) ? "" : nullToEmpty(page.seller.getVatNumber()));
        fields.put("seller.iban", page.seller.getIban() == null ? "" : dev.swirlit.indezy.util.BillingText.groupIban(page.seller.getIban()));
        fields.put("seller.bic", nullToEmpty(page.seller.getBic()));
        fields.put("client.name", nullToEmpty(page.buyer.getName()));
        fields.put("client.details", String.join("\n", page.buyerLines()));
        fields.put("client.address", nullToEmpty(page.buyer.getAddress()));
        fields.put("client.siren", nullToEmpty(page.buyer.getSiren()));
        fields.put("client.vatNumber", nullToEmpty(page.buyer.getVatNumber()));
        List<String[]> totals = page.totalRows();
        fields.put("totals.excludingVat", totals.getFirst()[1]);
        fields.put("totals.vat", totals.size() > 2 ? totals.get(1)[1] : "");
        fields.put("totals.toPay", totals.getLast()[1]);
        fields.put("mentions", String.join("\n", page.documentMentions()));
        fields.put("notes", nullToEmpty(page.notes()));
        fields.put("acceptance", nullToEmpty(page.acceptance()));
        List<Map<String, String>> lines = new ArrayList<>();
        for (InvoiceLine line : page.documentLines()) {
            String[] cells = page.cells(line);
            lines.add(Map.of("line.description", cells[0], "line.quantity", cells[1], "line.unitPrice", cells[2], "line.vatRate", cells[3],
                "line.amount", cells[4]));
        }
        return new Values(fields, lines, logo, page.seller.getLogoSize() == null ? LogoSize.MEDIUM : page.seller.getLogoSize(),
            Map.of("vatExempt", page.vatExempt()));
    }

    /** {@code invoice.number} and {@code quote.number} as {@code document.number}. */
    static String canonical(String placeholder) {
        if (placeholder.startsWith("invoice.") || placeholder.startsWith("quote.")) {
            return "document." + placeholder.substring(placeholder.indexOf('.') + 1);
        }
        return placeholder;
    }

    /** Each paragraph holding a placeholder written as one run, so that a placeholder split by Word reads whole. */
    static String merged(String xml) {
        Matcher paragraph = PARAGRAPH.matcher(xml);
        StringBuilder out = new StringBuilder();
        while (paragraph.find()) {
            String original = paragraph.group();
            // Breaks and tabs between the runs travel with the text as markers, and come back once it is one run.
            String text = original.contains("{{") ? BREAK.matcher(TAB.matcher(original).replaceAll("<w:t>" + TAB_MARK + "</w:t>"))
                .replaceAll("<w:t>" + BREAK_MARK + "</w:t>") : original;
            Matcher texts = TEXT.matcher(text);
            StringBuilder whole = new StringBuilder();
            int count = 0;
            while (texts.find()) {
                whole.append(texts.group(2));
                count++;
            }
            String joined = whole.toString();
            if (count > 1 && joined.contains("{{")) {
                Matcher rewrite = TEXT.matcher(text);
                StringBuilder rewritten = new StringBuilder();
                boolean first = true;
                while (rewrite.find()) {
                    String replacement = first ? "<w:t xml:space=\"preserve\">" + joined + "</w:t>" : "<w:t></w:t>";
                    rewrite.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
                    first = false;
                }
                rewrite.appendTail(rewritten);
                text = rewritten.toString();
            }
            text = text.replace(BREAK_MARK, "</w:t><w:br/><w:t xml:space=\"preserve\">").replace(TAB_MARK, "</w:t><w:tab/><w:t xml:space=\"preserve\">");
            if (count <= 1 || !joined.contains("{{")) {
                text = original;
            }
            paragraph.appendReplacement(out, Matcher.quoteReplacement(text));
        }
        paragraph.appendTail(out);
        return EMPTY_TEXT.matcher(out.toString()).replaceAll("<w:t></w:t>");
    }

    /**
     * Each part between {@code {{#case}}} and {@code {{/case}}} kept when the case holds and dropped otherwise, the part
     * after {@code {{^case}}} the other way round; its markers go either way.
     */
    static String cases(String xml, Map<String, Boolean> cases) {
        String result = xml;
        for (String name : CASES) {
            boolean holds = Boolean.TRUE.equals(cases.get(name));
            result = section(result, "{{#" + name + "}}", "{{/" + name + "}}", holds);
            result = section(result, "{{^" + name + "}}", "{{/" + name + "}}", !holds);
        }
        return result;
    }

    private static String section(String xml, String open, String close, boolean keep) {
        String result = xml;
        int start = result.indexOf(open);
        while (start >= 0) {
            int end = result.indexOf(close, start + open.length());
            if (end < 0) {
                return result.replace(open, "");
            }
            String inner = result.substring(start + open.length(), end);
            String kept = keep ? inner : keepStructure(inner);
            result = result.substring(0, start) + kept + result.substring(end + close.length());
            start = result.indexOf(open, start + kept.length());
        }
        return result;
    }

    /** What a dropped part leaves: its tags without their words, so the paragraphs around it stay whole. */
    private static String keepStructure(String inner) {
        return inner.replaceAll("(<w:t(?: [^>]*)?>)[^<]*(</w:t>)", "$1$2").replaceAll("^[^<]*", "").replaceAll("[^>]*$", "")
            .replaceAll("<w:drawing>.*?</w:drawing>", "");
    }

    /** The part between {@code {{#lines}}} and {@code {{/lines}}}, widened to its table rows or paragraphs, once per line. */
    static String repeatLines(String xml, List<Map<String, String>> lines) {
        int open = xml.indexOf("{{#lines}}");
        int close = xml.indexOf("{{/lines}}");
        if (open < 0 || close < open) {
            return xml.replace("{{#lines}}", "").replace("{{/lines}}", "");
        }
        String element = inside(xml, open, "w:tr") && inside(xml, close, "w:tr") ? "w:tr" : "w:p";
        int start = startOf(xml, open, element);
        int end = xml.indexOf("</" + element + ">", close) + element.length() + 3;
        String block = xml.substring(start, end).replace("{{#lines}}", "").replace("{{/lines}}", "");
        StringBuilder repeated = new StringBuilder();
        for (Map<String, String> line : lines) {
            repeated.append(substitute(block, line));
        }
        return xml.substring(0, start) + repeated + repeatLines(xml.substring(end), lines);
    }

    private static boolean inside(String xml, int at, String element) {
        int start = Math.max(xml.lastIndexOf("<" + element + ">", at), xml.lastIndexOf("<" + element + " ", at));
        return start >= 0 && xml.lastIndexOf("</" + element + ">", at) < start;
    }

    private static int startOf(String xml, int at, String element) {
        return Math.max(xml.lastIndexOf("<" + element + ">", at), xml.lastIndexOf("<" + element + " ", at));
    }

    /** The placeholders replaced by their values, line breaks kept; an unknown one left as written. */
    static String substitute(String xml, Map<String, String> values) {
        Matcher placeholder = PLACEHOLDER.matcher(xml);
        StringBuilder out = new StringBuilder();
        while (placeholder.find()) {
            String key = canonical(placeholder.group(1));
            String value = values.get(key);
            String replacement = value == null ? (PLACEHOLDERS.contains(key) && !key.startsWith("line.") ? "" : placeholder.group()) :
                runText(value);
            placeholder.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        placeholder.appendTail(out);
        return out.toString();
    }

    /** The mentions the template leaves out, added after its content, before its section properties. */
    private static String appendMissing(String xml, List<String> missing, Values values) {
        StringBuilder added = new StringBuilder();
        for (String mention : missing) {
            List<String> texts = new ArrayList<>();
            switch (mention) {
                case "number" -> texts.add(values.fields().getOrDefault("document.title", "") + " " +
                    values.fields().getOrDefault("document.number", values.fields().getOrDefault("document.draft", "")));
                case "date" -> texts.add(values.fields().getOrDefault("document.issueDate", ""));
                case "seller" -> {
                    texts.add(values.fields().get("seller.name"));
                    texts.add(values.fields().get("seller.details"));
                }
                case "client" -> {
                    texts.add(values.fields().get("client.name"));
                    texts.add(values.fields().get("client.details"));
                }
                case "lines" -> values.lines().forEach(line -> texts.add(line.get("line.description") + " — " + line.get("line.quantity") + " × " +
                    line.get("line.unitPrice") + " — " + line.get("line.vatRate") + " — " + line.get("line.amount")));
                case "totals" -> texts.add(values.fields().get("totals.excludingVat") + " · " + values.fields().get("totals.toPay"));
                case "mentions" -> texts.add(values.fields().get("mentions"));
                default -> {
                }
            }
            for (String text : texts) {
                if (text != null && !text.isBlank()) {
                    added.append("<w:p><w:r><w:rPr><w:sz w:val=\"16\"/></w:rPr><w:t xml:space=\"preserve\">")
                        .append(runText(text.strip())).append("</w:t></w:r></w:p>");
                }
            }
        }
        int section = xml.lastIndexOf("<w:sectPr");
        int body = xml.lastIndexOf("</w:body>");
        int at = section > xml.lastIndexOf("</w:p>") || section > xml.lastIndexOf("</w:tbl>") ? section : body;
        return at < 0 ? xml : xml.substring(0, at) + added + xml.substring(at);
    }

    private static String logoRun(Values values) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(values.logo()));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            return "";
        }
        double scale = Math.min((double) values.logoSize().width() / image.getWidth(), (double) values.logoSize().height() / image.getHeight());
        long cx = Math.round(image.getWidth() * scale * EMU_PER_POINT);
        long cy = Math.round(image.getHeight() * scale * EMU_PER_POINT);
        return "<w:r><w:drawing><wp:inline xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" distT=\"0\" " +
            "distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/><wp:docPr id=\"9001\" name=\"Logo\"/>" +
            "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:graphicData " +
            "uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
            "<pic:nvPicPr><pic:cNvPr id=\"9001\" name=\"logo\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip " +
            "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" r:embed=\"rIdIndezyLogo\"/><a:stretch><a:fillRect/>" +
            "</a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>";
    }

    /** The logo stored in the package, related from the document and its type declared. */
    private static void addLogo(Map<String, byte[]> parts, byte[] logo) {
        boolean png = logo.length > 3 && (logo[0] & 0xFF) == 0x89 && logo[1] == 'P';
        String extension = png ? "png" : "jpeg";
        parts.put("word/media/indezy-logo." + extension, logo.clone());
        String relsName = "word/_rels/document.xml.rels";
        String rels = parts.containsKey(relsName) ? new String(parts.get(relsName), StandardCharsets.UTF_8) :
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships " +
                "xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"></Relationships>";
        rels = rels.replace("</Relationships>", "<Relationship Id=\"rIdIndezyLogo\" " +
            "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/indezy-logo." + extension +
            "\"/></Relationships>");
        parts.put(relsName, rels.getBytes(StandardCharsets.UTF_8));
        String types = new String(parts.get("[Content_Types].xml"), StandardCharsets.UTF_8);
        if (!types.toLowerCase(Locale.ROOT).contains("extension=\"" + extension + "\"")) {
            types = types.replace("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">",
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"" + extension +
                    "\" ContentType=\"image/" + extension + "\"/>");
            parts.put("[Content_Types].xml", types.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** The parts the placeholders can be in: the document, its headers and its footers. */
    private static List<String> textParts(Map<String, byte[]> parts) {
        return parts.keySet().stream().filter(name -> name.equals(DOCUMENT) || name.matches("word/(header|footer)\\d*\\.xml")).toList();
    }

    private static Map<String, byte[]> parts(byte[] docx) {
        if (docx.length == 0 || docx.length > MAX_BYTES) {
            throw new IllegalArgumentException("A Word template holds 5 MB at most");
        }
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            long total = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (parts.size() >= MAX_ENTRIES) {
                    throw new IllegalArgumentException("This Word document holds too many parts");
                }
                byte[] content = zip.readNBytes(MAX_BYTES + 1);
                total += content.length;
                if (content.length > MAX_BYTES || total > 4L * MAX_BYTES) {
                    throw new IllegalArgumentException("This Word document is too large once unpacked");
                }
                parts.put(entry.getName(), content);
            }
        } catch (ZipException e) {
            throw new IllegalArgumentException("This file is not a Word document (.docx)", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!parts.containsKey(DOCUMENT) || !parts.containsKey("[Content_Types].xml")) {
            throw new IllegalArgumentException("This file is not a Word document (.docx)");
        }
        if (parts.keySet().stream().anyMatch(name -> name.toLowerCase(Locale.ROOT).contains("vbaproject"))) {
            throw new IllegalArgumentException("This Word document holds macros: save it as a .docx without them");
        }
        return parts;
    }

    private static byte[] zip(Map<String, byte[]> parts) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static Map<String, List<String>> requiredMentions() {
        Map<String, List<String>> required = new LinkedHashMap<>();
        required.put("number", List.of("document.number"));
        required.put("date", List.of("document.issueDate"));
        required.put("seller", List.of("seller.details", "seller.siret"));
        required.put("client", List.of("client.name", "client.details"));
        required.put("lines", List.of("#lines"));
        required.put("totals", List.of("totals.toPay"));
        required.put("mentions", List.of("mentions"));
        return required;
    }

    /** A value as the text of a run, each line break a {@code w:br}. */
    private static String runText(String value) {
        return String.join("</w:t><w:br/><w:t xml:space=\"preserve\">", value.lines().map(DocxDocument::escape).toList());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
