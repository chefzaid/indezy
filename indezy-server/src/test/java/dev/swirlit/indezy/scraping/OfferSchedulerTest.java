package dev.swirlit.indezy.scraping;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferSchedulerTest {

    private final OfferCollector collector = mock(OfferCollector.class);
    private final OfferRetention retention = mock(OfferRetention.class);
    private final OfferStore store = mock(OfferStore.class);
    private final OfferScheduler scheduler = new OfferScheduler(collector, retention, store);

    @Test
    void keysStoredOffersBatchAfterBatchUntilOneIsNotFull() {
        when(store.keyStoredOffers()).thenReturn(OfferStore.KEY_BATCH, OfferStore.KEY_BATCH, 12);
        when(store.keyStoredCompanies(0L)).thenReturn(812L);
        when(store.keyStoredCompanies(812L)).thenReturn(null);

        scheduler.keyStoredOffers();

        verify(store, times(3)).keyStoredOffers();
        verify(store).keyStoredCompanies(812L);
    }

    @Test
    void collectsAndPurgesOnSchedule() {
        scheduler.collectDueSites();
        scheduler.purgeOldOffers();

        verify(collector).collectDueSites();
        verify(retention).purge();
    }
}
