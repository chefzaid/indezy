package dev.swirlit.indezy.scraping;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the offer collection in the background when {@code indezy.offers.collection-enabled} is true:
 * looks for due sites every {@code indezy.offers.tick}, and removes old offers every night.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "indezy.offers.collection-enabled", havingValue = "true")
@RequiredArgsConstructor
public class OfferScheduler {

    private final OfferCollector collector;
    private final OfferRetention retention;
    private final OfferStore store;
    private final OfferRelocation relocation;

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${indezy.offers.tick:PT5M}")
    public void collectDueSites() {
        collector.collectDueSites();
    }

    /**
     * Groups the copies among offers stored before copies were told apart, keys the publishers of offers stored before
     * publishers had a key, cleans descriptions stored with markup escaped twice and locates the offers whose place was not
     * located when they were read; finds nothing to do once they are.
     */
    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT6H")
    public void keyStoredOffers() {
        int keyed;
        do {
            keyed = store.keyStoredOffers();
        } while (keyed == OfferStore.KEY_BATCH);
        Long last = 0L;
        do {
            last = store.keyStoredCompanies(last);
        } while (last != null);
        last = 0L;
        do {
            last = store.cleanStoredDescriptions(last);
        } while (last != null);
        last = 0L;
        do {
            last = relocation.locateStoredOffers(last);
        } while (last != null);
    }

    @Scheduled(cron = "0 15 4 * * *", zone = "Europe/Paris")
    public void purgeOldOffers() {
        retention.purge();
    }
}
