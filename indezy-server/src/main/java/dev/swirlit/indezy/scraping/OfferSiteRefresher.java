package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.OfferSiteStatusRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Reads a site on request, in the background: at most once every {@link OfferSiteRuns#REFRESH_EVERY} after its last
 * run or request, whoever asks, and never beside a run that holds it.
 */
@Slf4j
@Service
public class OfferSiteRefresher {

    /** What a refused request is told to wait while a reading runs. */
    static final Duration WAIT_FOR_RUN = Duration.ofMinutes(1);

    private final OfferSiteRuns runs;
    private final OfferSiteStatusRepository statuses;
    private final OfferCollector collector;
    private final OfferProperties properties;
    private final TaskExecutor executor;
    private final Clock clock;

    @Autowired
    public OfferSiteRefresher(OfferSiteRuns runs, OfferSiteStatusRepository statuses, OfferCollector collector, OfferProperties properties,
                              @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this(runs, statuses, collector, properties, executor, Clock.systemUTC());
    }

    OfferSiteRefresher(OfferSiteRuns runs, OfferSiteStatusRepository statuses, OfferCollector collector, OfferProperties properties,
                       TaskExecutor executor, Clock clock) {
        this.runs = runs;
        this.statuses = statuses;
        this.collector = collector;
        this.properties = properties;
        this.executor = executor;
        this.clock = clock;
    }

    /** Starts reading {@code site} and returns when it can be asked for again. */
    public Instant refresh(OfferSite site) {
        if (!properties.collectionEnabled()) {
            throw new InvalidStateException(ErrorMessages.OFFER_COLLECTION_OFF);
        }
        Instant now = clock.instant();
        if (!runs.requestRefresh(site, now)) {
            Instant availableAt = statuses.findById(site).map(OfferSiteRuns::refreshAvailableAt).orElse(null);
            Duration wait = availableAt != null && availableAt.isAfter(now) ? Duration.between(now, availableAt) : WAIT_FOR_RUN;
            throw new TooManyRequestsException(String.format(ErrorMessages.OFFER_REFRESH_TOO_SOON, site.getDisplayName()), wait);
        }
        log.info("Reading {} on request", site);
        executor.execute(() -> {
            if (runs.claim(site, clock.instant())) {
                collector.collect(site);
            }
        });
        return now.plus(OfferSiteRuns.REFRESH_EVERY);
    }
}
