package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.util.TextFolding;

import java.util.Map;

/** ISO codes of the countries job boards name in French or English. */
final class Countries {

    private static final Map<String, String> CODES = Map.ofEntries(
        Map.entry("france", "FR"),
        Map.entry("belgique", "BE"),
        Map.entry("belgium", "BE"),
        Map.entry("suisse", "CH"),
        Map.entry("switzerland", "CH"),
        Map.entry("luxembourg", "LU"),
        Map.entry("monaco", "MC"),
        Map.entry("espagne", "ES"),
        Map.entry("spain", "ES"),
        Map.entry("allemagne", "DE"),
        Map.entry("germany", "DE"),
        Map.entry("royaume uni", "GB"),
        Map.entry("united kingdom", "GB"),
        Map.entry("pays bas", "NL"),
        Map.entry("netherlands", "NL"),
        Map.entry("italie", "IT"),
        Map.entry("italy", "IT"),
        Map.entry("portugal", "PT"),
        Map.entry("maroc", "MA"),
        Map.entry("morocco", "MA"),
        Map.entry("tunisie", "TN"),
        Map.entry("canada", "CA"),
        Map.entry("bresil", "BR"),
        Map.entry("brazil", "BR"));

    private Countries() {
    }

    /** The code of a country name, or {@code null} when unknown. */
    static String code(String name) {
        return name == null ? null : CODES.get(TextFolding.words(name));
    }
}
