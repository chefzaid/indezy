package dev.swirlit.indezy.scraping;

import java.util.List;

/** Where a reader hands the offers of each page it reads. */
@FunctionalInterface
public interface OfferBatch {

    /** Stores one page of offers; {@code true} when the reader should go on to the next page. */
    boolean add(List<ScrapedOffer> offers);
}
