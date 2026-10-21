package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.service.OfferRateBenchmarkService;
import dev.swirlit.indezy.service.OfferSuspicionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the offer collection in the background when {@code indezy.offers.collection-enabled} is true:
 * looks for due sites every {@code indezy.offers.tick}, removes old offers every night, computes the medians offers'
 * daily rates are compared with and flags suspicious offers every hour.
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
    private final OfferRateBenchmarkService benchmarks;
    private final OfferSuspicionService suspicions;

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${indezy.offers.tick:PT5M}")
    public void collectDueSites() {
        collector.collectDueSites();
    }

    /**
     * Groups the copies among offers stored before copies were told apart, keys the publishers of offers stored before
     * publishers had a key, cleans descriptions stored with markup escaped twice, reads the remote policy their descriptions state for offers
     * without a work mode and locates the offers whose place was not located when they were read; finds nothing to do
     * once they are.
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
            last = store.readStoredRemotePolicies(last);
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

    /** The medians daily rates are compared with, each night once old offers are gone, and the suspicions they tell. */
    @Scheduled(cron = "0 45 4 * * *", zone = "Europe/Paris")
    public void computeRateBenchmarks() {
        benchmarks.refresh();
        suspicions.refresh();
    }

    /** Flags the offers collected since that look like fake or spam listings. */
    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT1H")
    public void flagSuspiciousOffers() {
        suspicions.refresh();
    }

    /** A server started before any night computes the medians once it is up. */
    @Scheduled(initialDelayString = "PT2M", fixedDelay = Long.MAX_VALUE)
    public void computeRateBenchmarksIfNone() {
        if (benchmarks.isEmpty()) {
            benchmarks.refresh();
        }
    }
}
