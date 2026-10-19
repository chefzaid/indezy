package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.JobPostings;
import dev.swirlit.indezy.scraping.OfferText;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.Sitemaps;
import dev.swirlit.indezy.util.TextFolding;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Next Mondays: its jobs sitemap lists every freelance job (old ones stay online; the feed's age limit leaves them
 * out), and each page carries a schema.org {@code JobPosting} with the daily rate plus a summary (duration).
 */
@Component
public class NextMondaysReader extends DetailPagesReader {

    static final String SITEMAP = "https://nextmondays.com/sitemap-jobs.xml";
    /** "6 mois", "1 an", "12 semaines". */
    private static final Pattern DURATION = Pattern.compile("(\\d{1,3})\\s*([a-z]+)");

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
            Matcher duration = DURATION.matcher(TextFolding.words(summary.getOrDefault("duree du contrat", "")));
            if (duration.find()) {
                completed.durationMonths(OfferText.durationMonths(Double.valueOf(duration.group(1)), duration.group(2)));
            }
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
        Map<String, String> summary = new HashMap<>();
        for (Element item : page.select(".summary-content-item")) {
            Element title = item.selectFirst(".summary-content-item-title");
            Element body = item.selectFirst(".summary-content-item-body");
            if (title != null && body != null && !body.text().isBlank()) {
                summary.put(TextFolding.words(title.text()), body.text().strip());
            }
        }
        return summary;
    }
}
