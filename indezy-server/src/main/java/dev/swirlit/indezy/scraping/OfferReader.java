package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.enums.OfferSite;

import java.io.IOException;

/** Reads the freelance offers of one job board, page after page. */
public interface OfferReader {

    OfferSite site();

    /** The registrable domain the reader may fetch from ("lehibou.com" covers api.lehibou.com). */
    String domain();

    /**
     * Whether the site lists its newest offers first, so a page holding only known offers ends the run.
     * Sites sorted otherwise are read up to the page budget on every run.
     */
    default boolean newestFirst() {
        return true;
    }

    void read(PageFetcher fetcher, OfferBatch batch) throws IOException, InterruptedException;
}
