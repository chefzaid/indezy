package dev.swirlit.indezy.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OfferMentionSchedulerTest {

    @Test
    void recordsThePendingOffersInBatches() {
        OfferMentionIndexer indexer = mock(OfferMentionIndexer.class);
        new OfferMentionScheduler(indexer).indexPending();
        verify(indexer).indexPending(OfferMentionScheduler.BATCHES_PER_RUN);
    }
}
