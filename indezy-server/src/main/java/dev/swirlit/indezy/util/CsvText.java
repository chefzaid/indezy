package dev.swirlit.indezy.util;

import java.util.Arrays;
import java.util.stream.Collectors;

/** CSV rows that spreadsheets open as written: fields quoted when needed, and typed text never read as a formula. */
public final class CsvText {

    private CsvText() {
    }

    /** A row of fields joined by the separator, each quoted when it holds the separator, a quote or a line break. */
    public static String row(String separator, String... fields) {
        return Arrays.stream(fields).map(field -> quoted(field, separator)).collect(Collectors.joining(separator));
    }

    /**
     * Typed text, prefixed with an apostrophe when a spreadsheet would read it as a formula (it starts with {@code =},
     * {@code +}, {@code -}, {@code @}, a tab or a carriage return), so a client named {@code =HYPERLINK(...)} stays text.
     */
    public static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    private static String quoted(String field, String separator) {
        if (field == null) {
            return "";
        }
        if (field.contains(separator) || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
