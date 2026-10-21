package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.PageFacts;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.Sitemaps;
import dev.swirlit.indezy.util.TextFolding;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Beyond Skills IT: one Webflow page lists its offers, labelled Freelance or CDI, and each offer's page gives its
 * label again, its place, work mode, daily rate, skill tags and description. Only the freelance offers' pages are read;
 * the pages give no date, so each offer is dated by its page's last change in the sitemap.
 */
@Component
public class BeyondSkillsItReader extends DetailPagesReader {

    static final String SITE_URL = "https://www.beyondskillsit.com";
    static final String LIST_URL = SITE_URL + "/offres-disponibles";
    static final String SITEMAP = SITE_URL + "/sitemap.xml";
    private static final String OFFERS = "/offres-disponibles/";

    @Override
    public OfferSite site() {
        return OfferSite.BEYOND_SKILLS_IT;
    }

    @Override
    public String domain() {
        return "beyondskillsit.com";
    }

    @Override
    List<Entry> entries(PageFetcher fetcher) throws IOException, InterruptedException {
        Map<String, Instant> changed = new HashMap<>();
        for (Sitemaps.Entry page : Sitemaps.read(fetcher, SITEMAP, url -> url.contains(OFFERS))) {
            if (page.lastModified() != null) {
                changed.put(page.url(), page.lastModified());
            }
        }
        return freelance(Jsoup.parse(fetcher.get(LIST_URL), LIST_URL), changed);
    }

    /** The list's freelance offers, with their sitemap day. */
    List<Entry> freelance(Document list, Map<String, Instant> changed) {
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // The page repeats its list for its filters: each offer is read once.
        for (Element card : list.select(".job-item:has(a[href*=/offres-disponibles/])")) {
            String url = card.selectFirst("a[href*=/offres-disponibles/]").absUrl("href");
            Element contract = card.selectFirst("[fs-cmsfilter-field=contrat]");
            String id = externalId(url);
            if (id != null && seen.add(url) && contract != null && "freelance".equals(TextFolding.words(contract.text()))) {
                entries.add(new Entry(url, id, changed.get(url)));
            }
        }
        return entries;
    }

    @Override
    String externalId(String url) {
        return after(url, OFFERS);
    }

    @Override
    ScrapedOffer offer(Entry entry, Document page) {
        Element title = page.selectFirst(".offres_title h1");
        if (title == null) {
            return null;
        }
        Element contract = page.selectFirst(".offres_category .jobs_category-link");
        Map<String, String> facts = PageFacts.written(page.select(".offres_title-wrapper .blogpost_details-wrapper:not(.w-condition-invisible)"));
        // "Paris / Full Remote".
        String place = facts.get("lieu");
        String rate = facts.get("tjm");
        Element description = page.selectFirst(".offres_content .text-rich-text");
        Instant changed = entry.lastModified();
        return ScrapedOffer.builder()
            // Titles open with an emoji ("🏛️ Architecte Frontend Vue.js").
            .title(title.text().replaceFirst("^[^\\p{L}\\p{N}]+", ""))
            .city(place == null ? null : place.split("/")[0].strip())
            .remoteText(facts.get("type"))
            .rateText(rate == null || !rate.matches(".*\\d.*") ? null : rate)
            .skills(page.select(".skills-collection-list .fiche-metier_skills-item").eachText())
            .description(description == null ? null : HtmlText.toText(description.html()))
            .publishedAt(changed)
            .publishedDayOnly(changed != null)
            .freelance(contract != null && "freelance".equals(TextFolding.words(contract.text())))
            .build();
    }
}
