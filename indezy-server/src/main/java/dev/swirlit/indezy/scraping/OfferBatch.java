package dev.swirlit.indezy.scraping;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Where a reader hands the offers of each page it reads, and asks which offers it already holds. */
@FunctionalInterface
public interface OfferBatch {

    /** Stores one page of offers; {@code true} when the reader should go on to the next page. */
    boolean add(List<ScrapedOffer> offers);

    /**
     * Which of these identifiers Indezy already holds, stored or recently set aside (closed to freelancers, too
     * old), so a reader of one page per offer fetches only the others.
     */
    default Set<String> known(Collection<String> externalIds) {
        return Set.of();
    }
}
