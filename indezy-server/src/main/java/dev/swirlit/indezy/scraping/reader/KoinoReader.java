package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Koïno: its sitemap lists every mission page with its last change, and each page shows whether applications are
 * open (the hidden status carries Webflow's {@code w-condition-invisible}), its facts ("TJM : selon profil",
 * "Localisation : Télétravail") and its description. Closed missions are handed on as closed, so they are set aside.
 */
@Component
public class KoinoReader extends DetailPagesReader {

    static final String SITEMAP = "https://www.koino.fr/sitemap.xml";
    private static final String MISSIONS = "/missions/";

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
        return Sitemaps.read(fetcher, SITEMAP, url -> url.contains(MISSIONS)).stream()
            .map(page -> new Entry(page.url(), page.url().substring(page.url().indexOf(MISSIONS) + MISSIONS.length()),
                page.lastModified()))
            .toList();
    }

    @Override
    ScrapedOffer offer(Entry entry, Document page) {
        Element title = page.selectFirst("h1");
        if (title == null) {
            return null;
        }
        Element status = page.selectFirst(".offer-status:not(.w-condition-invisible)");
        boolean open = status != null && TextFolding.words(status.text()).contains("ouverte");
        Map<String, String> facts = PageFacts.written(page.select(".offer-details-item"));
        String place = facts.get("localisation");
        boolean remote = place != null && TextFolding.words(place).matches(".*\\b(teletravail|remote|distance)\\b.*");
        // The status badges are paragraphs of the same header.
        Element summary = page.select(".offer-hero-section p").stream()
            .filter(paragraph -> paragraph.closest(".offer-status") == null)
            .findFirst().orElse(null);
        String sections = page.select("h2.heading-label, .cms-richtext").stream()
            .map(Element::outerHtml)
            .collect(Collectors.joining());
        String description = HtmlText.toText((summary == null ? "" : summary.outerHtml()) + sections);
        return ScrapedOffer.builder()
            .title(title.text())
            .description(description)
            .rateText(facts.get("tjm"))
            .city(remote ? null : place)
            .remoteText(remote ? place : null)
            .publishedAt(entry.lastModified())
            .publishedDayOnly(true)
            .freelance(open)
            .build();
    }
}
