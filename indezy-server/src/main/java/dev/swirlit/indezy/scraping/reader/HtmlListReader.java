package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.scraping.OfferBatch;
import dev.swirlit.indezy.scraping.OfferReader;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.SiteResponseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.List;

/**
 * A site that describes its offers in HTML list pages, newest first: page after page until one holds no offer, the
 * site has no further page, or the batch has enough.
 */
abstract class HtmlListReader implements OfferReader {

    /** The address of a list page, numbered from 1; {@code null} when the site has no such page. */
    abstract String pageUrl(int page);

    /** The offers a list page describes. */
    abstract List<ScrapedOffer> offers(Document page);

    @Override
    public void read(PageFetcher fetcher, OfferBatch batch) throws IOException, InterruptedException {
        for (int number = 1; pageUrl(number) != null; number++) {
            String url = pageUrl(number);
            List<ScrapedOffer> offers;
            try {
                offers = offers(Jsoup.parse(fetcher.get(url), url));
            } catch (SiteResponseException e) {
                // Past the last page, WordPress sites answer 404.
                if (number > 1 && e.getStatus() == 404) {
                    return;
                }
                throw e;
            }
            if (offers.isEmpty() || !batch.add(offers)) {
                return;
            }
        }
    }
}
