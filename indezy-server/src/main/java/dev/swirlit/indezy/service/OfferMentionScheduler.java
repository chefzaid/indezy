package dev.swirlit.indezy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Records every minute the profile terms the new or changed offers mention (see {@link OfferMentionIndexer}): off in
 * tests and in the one-off schema-migration and seed runs, with the offer collection.
 */
@Component
@ConditionalOnProperty(name = "indezy.offers.collection-enabled", havingValue = "true")
@RequiredArgsConstructor
public class OfferMentionScheduler {

    /** Batches recorded per run at most: 50,000 offers, a first run on an existing catalogue. */
    static final int BATCHES_PER_RUN = 100;

    private final OfferMentionIndexer indexer;

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void indexPending() {
        indexer.indexPending(BATCHES_PER_RUN);
    }
}
