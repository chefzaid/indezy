package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.HtmlText;
import dev.swirlit.indezy.scraping.JobPostings;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.Sitemaps;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * EasyPartner: its job sitemap lists every job page ({@code /job/<title>/}), each with a schema.org {@code JobPosting}
 * that gives its daily rate. Its freelance jobs ("Prestation") are {@code CONTRACTOR}; the {@code FULL_TIME} ones are
 * permanent jobs, left out. The posting's description only repeats the title, so the page's own text is read.
 */
@Component
public class EasyPartnerReader extends DetailPagesReader {

    static final String SITEMAP = "https://easypartner.fr/job-sitemap.xml";
    private static final String JOBS = "/job/";
    /** The page writes its sections as Markdown headings ("## Missions"), which the text keeps as plain lines. */
    private static final Pattern HEADING_MARKS = Pattern.compile("(?m)^#+\\s*");

    @Override
    public OfferSite site() {
        return OfferSite.EASY_PARTNER;
    }

    @Override
    public String domain() {
        return "easypartner.fr";
    }

    @Override
    List<Entry> entries(PageFetcher fetcher) throws IOException, InterruptedException {
        return Sitemaps.read(fetcher, SITEMAP, url -> externalId(url) != null).stream()
            .map(page -> new Entry(page.url(), externalId(page.url()), page.lastModified()))
            .toList();
    }

    @Override
    String externalId(String url) {
        return after(url, JOBS);
    }

    @Override
    ScrapedOffer offer(Entry entry, Document page) {
        return JobPostings.read(page).map(posting -> {
            Element content = page.selectFirst(".ep-job-content");
            if (content != null) {
                posting.description(HEADING_MARKS.matcher(HtmlText.toText(content.outerHtml())).replaceAll(""));
            }
            return posting.build();
        }).orElse(null);
    }
}
