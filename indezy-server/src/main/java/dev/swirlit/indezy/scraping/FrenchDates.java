package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.util.TextFolding;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the calendar dates job boards write: "5 octobre 2026", "mer. 14 oct. 2026", "19/10/2026", "2026-10-19", and
 * "16 oct" or "décembre" without a year, placed in the year that puts them closest to a reference day.
 */
public final class FrenchDates {

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
        Map.entry("janvier", 1), Map.entry("janv", 1), Map.entry("jan", 1), Map.entry("january", 1),
        Map.entry("fevrier", 2), Map.entry("fevr", 2), Map.entry("fev", 2), Map.entry("february", 2), Map.entry("feb", 2),
        Map.entry("mars", 3), Map.entry("mar", 3), Map.entry("march", 3),
        Map.entry("avril", 4), Map.entry("avr", 4), Map.entry("april", 4), Map.entry("apr", 4),
        Map.entry("mai", 5), Map.entry("may", 5),
        Map.entry("juin", 6), Map.entry("june", 6), Map.entry("jun", 6),
        Map.entry("juillet", 7), Map.entry("juil", 7), Map.entry("july", 7), Map.entry("jul", 7),
        Map.entry("aout", 8), Map.entry("august", 8), Map.entry("aug", 8),
        Map.entry("septembre", 9), Map.entry("sept", 9), Map.entry("sep", 9), Map.entry("september", 9),
        Map.entry("octobre", 10), Map.entry("oct", 10), Map.entry("october", 10),
        Map.entry("novembre", 11), Map.entry("nov", 11), Map.entry("november", 11),
        Map.entry("decembre", 12), Map.entry("dec", 12), Map.entry("december", 12));

    private static final int SHORT_NAME = 4;
    private static final Pattern NUMERIC = Pattern.compile("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})(?!\\d)");
    private static final Pattern ISO = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)");
    private static final Pattern WORDS = Pattern.compile("\\b(?:(\\d{1,2})(?:er)?\\s+)?([a-z]{3,9})\\.?(?:\\s+(\\d{4}))?\\b");

    /** France's time zone, where the boards' days begin. */
    public static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private FrenchDates() {
    }

    /** The instant a day begins in France; {@code null} stays {@code null}. */
    public static Instant startOfDay(LocalDate day) {
        return day == null ? null : day.atStartOfDay(PARIS).toInstant();
    }

    /** The first date in {@code text}, or {@code null}; a missing day is the 1st, a missing year the closest one. */
    public static LocalDate parse(String text, LocalDate reference) {
        String folded = TextFolding.fold(text);
        if (folded == null) {
            return null;
        }
        Matcher numeric = NUMERIC.matcher(folded);
        if (numeric.find()) {
            return date(Integer.parseInt(numeric.group(3)), Integer.parseInt(numeric.group(2)), Integer.parseInt(numeric.group(1)));
        }
        Matcher iso = ISO.matcher(folded);
        if (iso.find()) {
            return date(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)));
        }
        Matcher words = WORDS.matcher(folded);
        while (words.find()) {
            Integer month = MONTHS.get(words.group(2));
            // "mar." alone is Tuesday as often as March: a short month name needs a day or a year.
            boolean bare = words.group(1) == null && words.group(3) == null && words.group(2).length() <= SHORT_NAME;
            if (month == null || bare) {
                continue;
            }
            int day = words.group(1) == null ? 1 : Integer.parseInt(words.group(1));
            if (words.group(3) != null) {
                return date(Integer.parseInt(words.group(3)), month, day);
            }
            return closest(month, day, reference);
        }
        return null;
    }

    private static LocalDate closest(int month, int day, LocalDate reference) {
        LocalDate best = null;
        for (int year = reference.getYear() - 1; year <= reference.getYear() + 1; year++) {
            LocalDate candidate = date(year, month, day);
            if (candidate != null && (best == null ||
                Math.abs(ChronoUnit.DAYS.between(reference, candidate)) < Math.abs(ChronoUnit.DAYS.between(reference, best)))) {
                best = candidate;
            }
        }
        return best;
    }

    private static LocalDate date(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }
}
