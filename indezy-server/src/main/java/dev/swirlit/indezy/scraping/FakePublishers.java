package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.util.TextFolding;

import java.util.Set;

/**
 * Publishers known for fake listings (Free-Work's Digistrat, Propulse IT and Omicrone): Free-Work's reader drops their
 * offers, and their offers on other sites are flagged as suspicious.
 */
public final class FakePublishers {

    /** Their names' folded words, as a publisher's name starts. */
    static final Set<String> NAMES = Set.of("digistrat", "propulse it", "omicrone");

    private FakePublishers() {
    }

    /** Whether the publisher, written in any way, is one of them. */
    public static boolean named(String company) {
        String folded = TextFolding.words(company);
        return folded != null && NAMES.stream().anyMatch(folded::startsWith);
    }
}
