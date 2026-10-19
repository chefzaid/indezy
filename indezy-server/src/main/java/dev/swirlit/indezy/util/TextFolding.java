package dev.swirlit.indezy.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Folds text for matching: lower case, no accents, single spaces, so "Île-de-France" matches "ile de france". */
public final class TextFolding {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}+#.]+");

    private TextFolding() {
    }

    /** Lower case without accents; {@code null} stays {@code null}. */
    public static String fold(String text) {
        if (text == null) {
            return null;
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** {@link #fold} with every run of punctuation or spaces turned into one space, trimmed. */
    public static String words(String text) {
        if (text == null) {
            return null;
        }
        return SEPARATORS.matcher(fold(text)).replaceAll(" ").trim();
    }
}
