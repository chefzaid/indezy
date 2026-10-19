package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.util.TextFolding;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.Map;

/**
 * The labelled facts job boards show around an offer ("Télétravail" to "2 jours min."), keyed by the folded label
 * ("teletravail"); facts without a label or a value are left out.
 */
public final class PageFacts {

    private PageFacts() {
    }

    /** One fact per item, its label and its value in the given child elements. */
    public static Map<String, String> of(Elements items, String labelSelector, String valueSelector) {
        Map<String, String> facts = new HashMap<>();
        for (Element item : items) {
            Element label = item.selectFirst(labelSelector);
            Element value = item.selectFirst(valueSelector);
            if (label != null && value != null) {
                put(facts, label.text(), value.text());
            }
        }
        return facts;
    }

    /** One fact per item written "Label : value". */
    public static Map<String, String> written(Elements items) {
        Map<String, String> facts = new HashMap<>();
        for (Element item : items) {
            String text = item.text();
            int colon = text.indexOf(':');
            if (colon > 0) {
                put(facts, text.substring(0, colon), text.substring(colon + 1));
            }
        }
        return facts;
    }

    private static void put(Map<String, String> facts, String label, String value) {
        String key = TextFolding.words(label);
        if (!key.isEmpty() && !value.isBlank()) {
            facts.put(key, value.strip());
        }
    }
}
