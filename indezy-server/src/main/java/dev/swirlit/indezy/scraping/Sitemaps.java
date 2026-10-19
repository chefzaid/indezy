package dev.swirlit.indezy.scraping;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reads XML sitemaps (sitemaps.org): the pages of a {@code urlset} with their {@code lastmod}, following a
 * {@code sitemapindex} one level down. jsoup's XML parser never resolves external entities.
 */
public final class Sitemaps {

    /** A page of a sitemap and when it last changed ({@code null} when the sitemap does not say). */
    public record Entry(String url, Instant lastModified) {
    }

    private Sitemaps() {
    }

    /** Every page of the sitemap at {@code url} (and of the child sitemaps an index lists) that {@code keep} accepts. */
    public static List<Entry> read(PageFetcher fetcher, String url, Predicate<String> keep) throws IOException, InterruptedException {
        Document sitemap = Jsoup.parse(fetcher.get(url), "", Parser.xmlParser());
        List<Entry> entries = new ArrayList<>(entries(sitemap, keep));
        for (Element child : sitemap.select("sitemapindex > sitemap > loc")) {
            Document childSitemap = Jsoup.parse(fetcher.get(child.text().strip()), "", Parser.xmlParser());
            entries.addAll(entries(childSitemap, keep));
        }
        return entries;
    }

    static List<Entry> entries(Document sitemap, Predicate<String> keep) {
        List<Entry> entries = new ArrayList<>();
        for (Element page : sitemap.select("urlset > url")) {
            Element location = page.selectFirst("loc");
            String url = location == null ? "" : location.text().strip();
            if (!url.isEmpty() && keep.test(url)) {
                Element modified = page.selectFirst("lastmod");
                entries.add(new Entry(url, modified == null ? null : instant(modified.text().strip())));
            }
        }
        return entries;
    }

    /** A {@code lastmod}: a full timestamp, or a day read as its start in UTC. */
    static Instant instant(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException notADay) {
                return null;
            }
        }
    }
}
