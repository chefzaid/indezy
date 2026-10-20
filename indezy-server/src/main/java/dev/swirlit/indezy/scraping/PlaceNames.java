package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.util.TextFolding;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * Reads the commune a job board's place names, without what surrounds it: an arrondissement or a cedex ("Paris 13ème",
 * "Marseille Cedex 15"), remote words ("La Défense et remote", "100% Télétravail (Toulouse)"), a list of places (the
 * first one is kept), a short "St" for Saint, and business districts or short forms, named after the commune that holds
 * them.
 */
public final class PlaceNames {

    /** Business districts and short forms, folded, and the commune that holds them. */
    static final Map<String, String> DISTRICTS = Map.ofEntries(
        entry("la defense", "Puteaux"),
        entry("paris la defense", "Puteaux"),
        entry("sophia antipolis", "Valbonne"),
        entry("la part dieu", "Lyon"),
        entry("la plaine saint denis", "Saint-Denis"),
        entry("saint quentin en yvelines", "Montigny-le-Bretonneux"),
        entry("sqy", "Montigny-le-Bretonneux"),
        entry("porte d ivry", "Paris"),
        entry("marne la vallee", "Champs-sur-Marne"),
        entry("euromediterranee", "Marseille"),
        entry("plateau de saclay", "Saclay"),
        entry("roissy", "Roissy-en-France"),
        entry("roissy cdg", "Roissy-en-France"),
        entry("roissy charles de gaulle", "Roissy-en-France"));

    private static final Pattern PARENTHESES = Pattern.compile("\\(([^()]*)\\)");
    /** What separates the places of a list: "Lille, Dijon ou Toulouse", "Malakoff/Corbeil", "La Défense et remote". */
    private static final Pattern LIST = Pattern.compile("\\s*[,;/|+]\\s*|\\s+(?:ou|et|or|and|&)\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CEDEX = Pattern.compile("\\s+cedex\\b.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARRONDISSEMENT = Pattern.compile("^(paris|lyon|marseille)\\s+\\d{1,2}\\D*$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern REMOTE_WORDS = Pattern.compile(
        "\\d{1,3}\\s*%|\\b(?:full|remote|t[eé]l[eé]travail|hybride?|partiel|sur site|on site)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SAINT = Pattern.compile("^(st|ste)\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern LETTER = Pattern.compile("\\p{L}");

    private PlaceNames() {
    }

    /** The commune {@code place} names, or {@code null} when it names none (blank, or remote words only). */
    public static String commune(String place) {
        if (place == null || place.isBlank()) {
            return null;
        }
        String text = place.strip();
        Matcher inside = PARENTHESES.matcher(text);
        if (inside.find()) {
            String outside = PARENTHESES.matcher(text).replaceAll(" ").strip();
            text = clean(outside) == null ? inside.group(1) : outside;
        }
        for (String part : LIST.split(text)) {
            String commune = clean(part);
            if (commune != null) {
                return DISTRICTS.getOrDefault(TextFolding.words(commune), commune);
            }
        }
        return null;
    }

    private static String clean(String part) {
        String text = CEDEX.matcher(part).replaceAll("").strip();
        Matcher arrondissement = ARRONDISSEMENT.matcher(text);
        if (arrondissement.matches()) {
            text = arrondissement.group(1);
        }
        text = REMOTE_WORDS.matcher(text).replaceAll(" ").replaceAll("\\s+", " ").strip();
        text = SAINT.matcher(text).replaceFirst(saint -> saint.group(1).equalsIgnoreCase("ste") ? "Sainte-" : "Saint-");
        return LETTER.matcher(text).find() ? text : null;
    }
}
