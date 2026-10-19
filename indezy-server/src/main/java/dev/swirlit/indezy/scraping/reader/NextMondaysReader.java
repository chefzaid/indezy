package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.JobPostings;
import dev.swirlit.indezy.scraping.OfferText;
import dev.swirlit.indezy.scraping.PageFacts;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.Sitemaps;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Next Mondays: its jobs sitemap lists every freelance job (old ones stay online; the feed's age limit leaves them
 * out), and each page carries a schema.org {@code JobPosting} with the daily rate plus a summary (duration).
 */
@Component
public class NextMondaysReader extends DetailPagesReader {

    static final String SITEMAP = "https://nextmondays.com/sitemap-jobs.xml";

    @Override
    public OfferSite site() {
        return OfferSite.NEXT_MONDAYS;
    }

    @Override
    public String domain() {
        return "nextmondays.com";
    }

    @Override
    List<Entry> entries(PageFetcher fetcher) throws IOException, InterruptedException {
        return Sitemaps.read(fetcher, SITEMAP, url -> url.contains("/jobs/")).stream()
            .map(page -> new Entry(page.url(), page.url().substring(page.url().lastIndexOf('/') + 1), page.lastModified()))
            .toList();
    }

    @Override
    ScrapedOffer offer(Entry entry, Document page) {
        return JobPostings.read(page).map(posting -> {
            Map<String, String> summary = summary(page);
            ScrapedOffer offer = posting.build();
            ScrapedOffer.ScrapedOfferBuilder completed = offer.toBuilder();
            completed.durationMonths(OfferText.duration(summary.get("duree du contrat")));
            if (offer.getCity() == null) {
                completed.city(summary.get("localisation"));
            }
            if (offer.getRateMin() == null && offer.getRateMax() == null) {
                completed.rateText(summary.get("tarif indicatif"));
            }
            return completed.build();
        }).orElse(null);
    }

    /** The job summary ("durée du contrat" to "6 mois"), keyed by the folded title. */
    static Map<String, String> summary(Document page) {
        return PageFacts.of(page.select(".summary-content-item"), ".summary-content-item-title", ".summary-content-item-body");
    }
}
