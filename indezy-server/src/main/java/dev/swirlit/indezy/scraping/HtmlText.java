package dev.swirlit.indezy.scraping;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Turns the HTML of a job board into plain text, keeping paragraphs and list items on their own lines. */
public final class HtmlText {

    /** Longest description kept, in characters. */
    public static final int MAX_LENGTH = 20_000;

    private static final String BREAK = " ";
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\u00A0]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private HtmlText() {
    }

    /** Text with its HTML entities decoded ("S&#xE9;curit&#xE9;" is "Sécurité"); {@code null} stays {@code null}. */
    public static String unescape(String text) {
        return text == null ? null : Parser.unescapeEntities(text, false);
    }

    /** The texts given that are not blank, one after the other; {@code null} when there is none. */
    public static String join(String separator, String... texts) {
        String joined = Stream.of(texts).filter(text -> text != null && !text.isBlank()).collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
    }

    /** Plain text of an HTML fragment, or {@code null} when it holds no text. */
    public static String toText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        document.select("br").after(BREAK);
        document.select("p, div, h1, h2, h3, h4, h5, h6, tr, ul, ol").before(BREAK).after(BREAK);
        for (Element item : document.select("li")) {
            item.prepend(BREAK + "• ");
        }
        String flat = Jsoup.clean(document.body().html(), "", Safelist.none(),
            new Document.OutputSettings().prettyPrint(false));
        String text = Parser.unescapeEntities(flat, false).replace(BREAK, "\n");
        StringBuilder lines = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            lines.append(SPACES.matcher(line).replaceAll(" ").strip()).append('\n');
        }
        String result = BLANK_LINES.matcher(lines.toString()).replaceAll("\n\n").strip();
        if (result.isEmpty()) {
            return null;
        }
        return result.length() > MAX_LENGTH ? result.substring(0, MAX_LENGTH) : result;
    }
}
