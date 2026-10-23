package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

/** A site that lists its offers (a sitemap, an API) and describes each on its own page. */
abstract class DetailPagesReader extends OfferDetailsReader {

    /** The offer of an entry's page, or {@code null} when the page holds none. */
    abstract ScrapedOffer offer(Entry entry, Document page);

    @Override
    final ScrapedOffer details(PageFetcher fetcher, Entry entry) throws IOException, InterruptedException {
        return offer(entry, Jsoup.parse(fetcher.get(entry.url()), entry.url()));
    }
}
