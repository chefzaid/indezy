package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.FrenchDates;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.PageFacts;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.Sitemaps;
import dev.swirlit.indezy.util.TextFolding;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Koïno: its missions sitemap lists the missions open to applications with their last change, and each mission page
 * is labelled "Mission freelance", states whether applications are open ({@code data-mission-etat="ouverte"}, else
 * {@code "fermee"}), its facts ("Lieu" to "Télétravail, Paris", "TJM" to "Selon profil"), its publication day and
 * its description. Closed missions are handed on as closed, so they are set aside.
 */
@Component
public class KoinoReader extends DetailPagesReader {

    /** The open missions; the main sitemap no longer lists any. */
    static final String SITEMAP = "https://www.koino.fr/sitemap-missions.xml";
    private static final String MISSIONS = "/missions/";
    private static final String OPEN = "ouverte";
    private static final Pattern REMOTE = Pattern.compile("\\b(teletravail|remote|distance)\\b");

    @Override
    public OfferSite site() {
        return OfferSite.KOINO;
    }

    @Override
    public String domain() {
        return "koino.fr";
    }

    @Override
    List<Entry> entries(PageFetcher fetcher) throws IOException, InterruptedException {
        return Sitemaps.read(fetcher, SITEMAP, url -> externalId(url) != null).stream()
            .map(page -> new Entry(page.url(), externalId(page.url()), page.lastModified()))
            .toList();
    }

    @Override
    String externalId(String url) {
        return after(url, MISSIONS);
    }

    @Override
    ScrapedOffer offer(Entry entry, Document page) {
        Element mission = page.selectFirst("main[data-mission-etat]");
        Element title = page.selectFirst("h1");
        if (mission == null || title == null) {
            return null;
        }
        Element label = page.selectFirst(".mi-sur");
        boolean freelance = label != null && TextFolding.words(label.text()).contains("freelance");
        boolean open = OPEN.equals(mission.attr("data-mission-etat"));
        Map<String, String> facts = PageFacts.of(page.select(".mi-tuile"), "dt", "dd");
        // "Télétravail, Paris": the remote work, then the town.
        String place = facts.get("lieu");
        boolean remote = place != null && remote(place);
        String city = place == null ? null : Arrays.stream(place.split(","))
            .map(String::strip)
            .filter(part -> !part.isEmpty() && !remote(part))
            .collect(Collectors.joining(", "));
        // The full description repeats the short sections above it.
        Element summary = page.selectFirst(".chapo");
        Element full = page.selectFirst(".mi-complet-c");
        String sections = full != null ? full.outerHtml() : page.select(".mi-corps .mi-bloc").outerHtml();
        Element published = page.selectFirst(".mi-faits-pied time[datetime]");
        LocalDate day = published == null ? null : FrenchDates.parse(published.attr("datetime"), LocalDate.now(FrenchDates.PARIS));
        return ScrapedOffer.builder()
            .title(title.text())
            .description(HtmlText.toText((summary == null ? "" : summary.outerHtml()) + sections))
            .rateText(facts.get("tjm"))
            .city(city == null || city.isEmpty() ? null : city)
            .remoteText(remote ? place : null)
            .publishedAt(day != null ? FrenchDates.startOfDay(day) : entry.lastModified())
            .publishedDayOnly(true)
            .freelance(freelance && open)
            .build();
    }

    private static boolean remote(String text) {
        return REMOTE.matcher(TextFolding.words(text)).find();
    }
}
