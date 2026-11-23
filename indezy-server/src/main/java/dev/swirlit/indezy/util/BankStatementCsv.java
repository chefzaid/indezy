package dev.swirlit.indezy.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The credits of a bank statement exported as CSV, as French banks write them (see docs/features.md#bank-reconciliation):
 * a separator among {@code ;}, {@code ,} and tab, lines before the header skipped, a date column, a label column, and
 * either one amount column (credits positive) or a credit and a debit column; amounts with a decimal comma or point,
 * spaces and a currency sign, dates as {@code 31/12/2026}, {@code 31-12-2026}, {@code 31.12.2026}, {@code 31/12/26} or
 * {@code 2026-12-31}.
 */
public final class BankStatementCsv {

    /** At most this many lines are read. */
    public static final int MAX_LINES = 5000;

    private static final List<DateTimeFormatter> DATES = Stream.of("dd/MM/uuuu", "dd-MM-uuuu", "dd.MM.uuuu", "dd/MM/uu", "uuuu-MM-dd", "d/M/uuuu")
        .map(pattern -> DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT)).toList();

    private BankStatementCsv() {
    }

    /** A credit on the account: its day, its label as the bank wrote it, its amount. */
    public record Credit(LocalDate date, String label, BigDecimal amount) {
    }

    /** The statement's credits in its order; none when no header with a date, a label and an amount is found. */
    public static List<Credit> credits(String content) {
        List<Credit> credits = new ArrayList<>();
        if (content == null) {
            return credits;
        }
        String[] lines = content.replace("﻿", "").split("\\r?\\n", MAX_LINES + 1);
        int header = -1;
        char separator = ';';
        Columns columns = null;
        for (int index = 0; index < Math.min(lines.length, 30) && columns == null; index++) {
            char candidate = separatorOf(lines[index]);
            Columns found = Columns.of(fields(lines[index], candidate));
            if (found != null) {
                header = index;
                separator = candidate;
                columns = found;
            }
        }
        if (columns == null) {
            return credits;
        }
        for (int index = header + 1; index < Math.min(lines.length, MAX_LINES); index++) {
            if (lines[index].isBlank()) {
                continue;
            }
            List<String> fields = fields(lines[index], separator);
            LocalDate date = date(at(fields, columns.date()));
            BigDecimal amount = columns.amount() >= 0 ? amount(at(fields, columns.amount())) : amount(at(fields, columns.credit()));
            if (columns.amount() < 0 && amount != null && amount.signum() < 0) {
                amount = amount.negate();
            }
            if (date != null && amount != null && amount.signum() > 0) {
                credits.add(new Credit(date, at(fields, columns.label()).strip(), amount));
            }
        }
        return credits;
    }

    /** The column of each value: an amount column, or a credit column (debits are not read). */
    private record Columns(int date, int label, int amount, int credit) {

        static Columns of(List<String> headers) {
            int date = -1;
            int label = -1;
            int amount = -1;
            int credit = -1;
            for (int index = 0; index < headers.size(); index++) {
                String header = TextFolding.words(headers.get(index));
                if (date < 0 && header.startsWith("date")) {
                    date = index;
                } else if (label < 0 && (header.startsWith("libelle") || header.startsWith("label") || header.startsWith("description") ||
                    header.startsWith("intitule") || header.startsWith("detail") || header.startsWith("motif") || header.startsWith("operation"))) {
                    label = index;
                } else if (amount < 0 && (header.startsWith("montant") || header.startsWith("amount"))) {
                    amount = index;
                } else if (credit < 0 && header.startsWith("credit")) {
                    credit = index;
                }
            }
            return date >= 0 && label >= 0 && (amount >= 0 || credit >= 0) ? new Columns(date, label, amount, credit) : null;
        }
    }

    private static char separatorOf(String line) {
        long semicolons = line.chars().filter(character -> character == ';').count();
        long tabs = line.chars().filter(character -> character == '\t').count();
        long commas = line.chars().filter(character -> character == ',').count();
        if (semicolons >= tabs && semicolons >= commas && semicolons > 0) {
            return ';';
        }
        return tabs >= commas && tabs > 0 ? '\t' : ',';
    }

    /** The fields of a line, double quotes honoured. */
    static List<String> fields(String line, char separator) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == separator && !quoted) {
                fields.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        fields.add(current.toString().strip());
        return fields;
    }

    private static String at(List<String> fields, int index) {
        return index >= 0 && index < fields.size() ? fields.get(index) : "";
    }

    /** A day in one of the statement formats; null when none reads it. */
    static LocalDate date(String text) {
        String value = text.strip();
        for (DateTimeFormatter format : DATES) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException notThisOne) {
                // The next format may read it.
            }
        }
        return null;
    }

    /** An amount with a decimal comma or point, spaces, a sign and a currency; null when it is not one. */
    static BigDecimal amount(String text) {
        String value = text.replaceAll("[\\s  €$]|EUR", "").replace("+", "");
        if (value.isEmpty()) {
            return null;
        }
        int comma = value.lastIndexOf(',');
        int point = value.lastIndexOf('.');
        if (comma > point) {
            value = value.replace(".", "").replace(',', '.');
        } else if (point > comma && comma >= 0) {
            value = value.replace(",", "");
        }
        try {
            return new BigDecimal(value.toUpperCase(Locale.ROOT));
        } catch (NumberFormatException notAnAmount) {
            return null;
        }
    }
}
