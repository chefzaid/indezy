package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.JobPostings;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.Sitemaps;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * FreelanceRepublik: besides matching the profiles it holds, it publishes missions, each listed in its sitemap
 * ({@code /missions/<title>-<id>}) and described by a schema.org {@code JobPosting} ({@code CONTRACTOR}, with the
 * publication date, place and full description). The platform names itself as the publisher.
 */
@Component
public class FreelanceRepublikReader extends DetailPagesReader {

    static final String SITEMAP = "https://www.freelancerepublik.com/sitemap.xml";
    private static final String MISSIONS = "/missions/";

    @Override
    public OfferSite site() {
        return OfferSite.FREELANCE_REPUBLIK;
    }

    @Override
    public String domain() {
        return "freelancerepublik.com";
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
        return JobPostings.read(page).map(posting -> {
            ScrapedOffer offer = posting.build();
            String city = offer.getCity();
            // The locality repeats the region and the country ("Paris, Paris, France").
            return city == null || !city.contains(",") ? offer : offer.toBuilder().city(city.substring(0, city.indexOf(',')).strip()).build();
        }).orElse(null);
    }
}
