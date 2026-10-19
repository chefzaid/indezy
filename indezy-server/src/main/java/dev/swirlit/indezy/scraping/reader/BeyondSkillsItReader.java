package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.OfferBatch;
import dev.swirlit.indezy.scraping.OfferReader;
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
 * Beyond Skills IT: one Webflow page lists its offers, labelled Freelance or CDI, with the place, work mode and
 * daily rate; the cards give no date, so each offer is dated by its page's last change in the sitemap.
 */
@Component
public class BeyondSkillsItReader implements OfferReader {

    static final String SITE_URL = "https://www.beyondskillsit.com";
    static final String LIST_URL = SITE_URL + "/offres-disponibles";
    static final String SITEMAP = SITE_URL + "/sitemap.xml";

    @Override
    public OfferSite site() {
        return OfferSite.BEYOND_SKILLS_IT;
    }

    @Override
    public String domain() {
        return "beyondskillsit.com";
    }

    @Override
    public void read(PageFetcher fetcher, OfferBatch batch) throws IOException, InterruptedException {
        Map<String, Instant> changed = new HashMap<>();
        for (Sitemaps.Entry page : Sitemaps.read(fetcher, SITEMAP, url -> url.contains("/offres-disponibles/"))) {
            if (page.lastModified() != null) {
                changed.put(page.url(), page.lastModified());
            }
        }
        batch.add(offers(Jsoup.parse(fetcher.get(LIST_URL), LIST_URL), changed));
    }

    static List<ScrapedOffer> offers(Document page, Map<String, Instant> changed) {
        List<ScrapedOffer> offers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // The page repeats its list for its filters: each offer is read once.
        for (Element card : page.select(".job-item:has(a[href*=/offres-disponibles/])")) {
            Element link = card.selectFirst("a[href*=/offres-disponibles/]");
            String url = link.absUrl("href");
            Element contract = card.selectFirst("[fs-cmsfilter-field=contrat]");
            Element title = card.selectFirst("[fs-cmsfilter-field=name]");
            if (!seen.add(url) || title == null || contract == null || !"freelance".equals(TextFolding.words(contract.text()))) {
                continue;
            }
            Map<String, String> facts = PageFacts.of(card.select(".jobs_bottom-items_left_place"),
                ".jobs_bottom-items_left_place-text", ".text-weight-semibold");
            Element rate = card.selectFirst(".jobs_bottom-items_right:not(.w-condition-invisible) [fs-cmsfilter-field=salaire]");
            Element region = card.selectFirst("[fs-cmsfilter-field=region]");
            // "Paris / Full Remote".
            String place = facts.get("lieu");
            offers.add(ScrapedOffer.builder()
                .externalId(url.substring(url.lastIndexOf('/') + 1))
                .url(url)
                // Titles open with an emoji ("🏛️ Architecte Frontend Vue.js").
                .title(title.text().replaceFirst("^[^\\p{L}\\p{N}]+", ""))
                .city(place == null ? null : place.split("/")[0].strip())
                .regionName(region == null ? null : region.text().split(",")[0].strip())
                .remoteText(facts.get("type"))
                .rateText(rate == null ? null : rate.text())
                .publishedAt(changed.get(url))
                .publishedDayOnly(changed.containsKey(url))
                .build());
        }
        return offers;
    }
}
