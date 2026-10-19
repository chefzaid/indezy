package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.OfferSiteStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Schedule and health of each site's runs: who may run a site now, and how its last run went. */
@Service
@RequiredArgsConstructor
public class OfferSiteRuns {

    /** A failing site waits its interval doubled per failure, up to this long. */
    static final Duration MAX_BACKOFF = Duration.ofHours(24);
    private static final int MAX_ERROR_LENGTH = 500;

    private final OfferSiteStatusRepository statuses;
    private final OfferProperties properties;

    /** Whether this caller may read {@code site} now: it is due and no other run holds it. */
    @Transactional
    public boolean claim(OfferSite site, Instant now) {
        if (!statuses.existsById(site)) {
            statuses.saveAndFlush(new OfferSiteStatus(site, now));
        }
        return statuses.claim(site, now, now.minus(properties.staleRunAfter())) == 1;
    }

    @Transactional
    public void succeeded(OfferSite site, Instant now, int found, int created) {
        OfferSiteStatus status = statuses.findById(site).orElseGet(() -> new OfferSiteStatus(site, now));
        status.setRunningSince(null);
        status.setLastRunAt(now);
        status.setLastSuccessAt(now);
        status.setLastError(null);
        status.setLastFound(found);
        status.setLastNew(created);
        status.setConsecutiveFailures(0);
        status.setNextRunAt(now.plus(site.getRefreshInterval()));
        statuses.save(status);
    }

    @Transactional
    public void failed(OfferSite site, Instant now, String error) {
        OfferSiteStatus status = statuses.findById(site).orElseGet(() -> new OfferSiteStatus(site, now));
        int failures = status.getConsecutiveFailures() + 1;
        Duration wait = site.getRefreshInterval().multipliedBy(1L << Math.min(failures, 10));
        status.setRunningSince(null);
        status.setLastRunAt(now);
        status.setLastError(error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH)));
        status.setConsecutiveFailures(failures);
        status.setNextRunAt(now.plus(wait.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : wait));
        statuses.save(status);
    }
}
