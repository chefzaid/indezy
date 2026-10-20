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
    private final OfferRelocation relocation = mock(OfferRelocation.class);
    private final OfferScheduler scheduler = new OfferScheduler(collector, retention, store, relocation);

    @Test
    void keysStoredOffersBatchAfterBatchUntilOneIsNotFull() {
        when(store.keyStoredOffers()).thenReturn(OfferStore.KEY_BATCH, OfferStore.KEY_BATCH, 12);
        when(store.keyStoredCompanies(0L)).thenReturn(812L);
        when(store.keyStoredCompanies(812L)).thenReturn(null);
        when(store.cleanStoredDescriptions(0L)).thenReturn(null);
        when(store.readStoredRemotePolicies(0L)).thenReturn(null);
        when(relocation.locateStoredOffers(0L)).thenReturn(50L);
        when(relocation.locateStoredOffers(50L)).thenReturn(null);

        scheduler.keyStoredOffers();

        verify(store, times(3)).keyStoredOffers();
        verify(store).keyStoredCompanies(812L);
        verify(store).cleanStoredDescriptions(0L);
        verify(store).readStoredRemotePolicies(0L);
        verify(relocation).locateStoredOffers(50L);
    }

    @Test
    void collectsAndPurgesOnSchedule() {
        scheduler.collectDueSites();
        scheduler.purgeOldOffers();

        verify(collector).collectDueSites();
        verify(retention).purge();
    }
}
