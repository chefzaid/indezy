package dev.swirlit.indezy.scraping;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Reads what a link shows, for its preview card under a post (see docs/features.md#member-network): the site's name,
 * the page's title and description, from its Open Graph tags else its title and description; never an image, so readers
 * load nothing from the linked site. Kept to the page's own site and to the public internet ({@link PublicHosts}).
 */
@Component
public class LinkPreviewReader {

    static final int TITLE_MAX = 200;
    static final int DESCRIPTION_MAX = 300;

    /** What a link shows. */
    public record Preview(String url, String site, String title, String description) {
    }

    private final SiteHttpClient http;
    private final PublicHosts.Resolver resolver;

    @Autowired
    public LinkPreviewReader(SiteHttpClient http) {
        this(http, InetAddress::getAllByName);
    }

    LinkPreviewReader(SiteHttpClient http, PublicHosts.Resolver resolver) {
        this.http = http;
        this.resolver = resolver;
    }

    /** The preview of the page at {@code url}; none for a page out of reach or without a title. */
    public Optional<Preview> read(String url) throws InterruptedException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        Predicate<String> isPublic = name -> PublicHosts.isPublic(name, resolver);
        if (!scheme.equals("https") && !scheme.equals("http") || uri.getPort() != -1 || !isPublic.test(host)) {
            return Optional.empty();
        }
        String html;
        try {
            html = http.fetcherFor(host.replaceFirst("^www\\.", ""), isPublic).get(uri.toString());
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
        return preview(Jsoup.parse(html, uri.toString()), uri.toString(), host);
    }

    static Optional<Preview> preview(Document page, String url, String host) {
        String title = firstOf(meta(page, "og:title"), page.title());
        if (title == null) {
            return Optional.empty();
        }
        String site = firstOf(meta(page, "og:site_name"), host.replaceFirst("^www\\.", ""));
        String description = firstOf(meta(page, "og:description"), meta(page, "description"));
        return Optional.of(new Preview(url, cut(site, TITLE_MAX), cut(title, TITLE_MAX), cut(description, DESCRIPTION_MAX)));
    }

    private static String meta(Document page, String name) {
        Element meta = page.selectFirst("meta[property=" + name + "], meta[name=" + name + "]");
        return meta == null ? null : meta.attr("content");
    }

    private static String firstOf(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.replaceAll("\\s+", " ").strip();
            }
        }
        return null;
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
