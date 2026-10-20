package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.OfferSiteStatusRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Metrics of the offer collection, scraped by Prometheus from {@code /api/actuator/prometheus}: per site, the runs and
 * how they ended, the offers they found and saw for the first time and how long they took (counters of this replica),
 * and, from the shared site statuses, its failed runs in a row, what its last successful run found and how long ago it
 * succeeded (gauges, the same on every replica; read from the database at most once every {@value #SNAPSHOT_SECONDS}
 * seconds). Geocoding timeouts are counted too.
 */
@Component
public class OfferMetrics {

    static final long SNAPSHOT_SECONDS = 60;

    private final MeterRegistry registry;
    private final OfferSiteStatusRepository statuses;
    private final Clock clock;
    private volatile Map<OfferSite, OfferSiteStatus> snapshot = Map.of();
    private volatile Instant snapshotAt = Instant.MIN;

    @Autowired
    public OfferMetrics(MeterRegistry registry, OfferSiteStatusRepository statuses) {
        this(registry, statuses, Clock.systemUTC());
    }

    OfferMetrics(MeterRegistry registry, OfferSiteStatusRepository statuses, Clock clock) {
        this.registry = registry;
        this.statuses = statuses;
        this.clock = clock;
        for (OfferSite site : OfferSite.values()) {
            gauge("indezy.offers.site.consecutive.failures", site, null, status -> status.getConsecutiveFailures());
            gauge("indezy.offers.site.last.found", site, null,
                status -> status.getLastFound() == null ? Double.NaN : status.getLastFound());
            gauge("indezy.offers.site.last.success.age", site, "seconds", status -> status.getLastSuccessAt() == null ? Double.NaN :
                Duration.between(status.getLastSuccessAt(), clock.instant()).toSeconds());
        }
    }

    /** A run of {@code site} ended; {@code duration} is {@code null} when its start is unknown. */
    public void runEnded(OfferSite site, boolean succeeded, int found, int created, Duration duration) {
        registry.counter("indezy.offers.runs", "site", site.name(), "outcome", succeeded ? "success" : "failure").increment();
        if (succeeded) {
            registry.counter("indezy.offers.found", "site", site.name()).increment(found);
            registry.counter("indezy.offers.new", "site", site.name()).increment(created);
        }
        if (duration != null && !duration.isNegative()) {
            registry.timer("indezy.offers.run.duration", "site", site.name()).record(duration);
        }
    }

    public void geocodingTimedOut() {
        registry.counter("indezy.offers.geocoding.timeouts").increment();
    }

    private void gauge(String name, OfferSite site, String unit, ToDoubleFunction<OfferSiteStatus> value) {
        Gauge.builder(name, this, metrics -> {
                OfferSiteStatus status = metrics.statuses().get(site);
                return status == null ? Double.NaN : value.applyAsDouble(status);
            })
            .tag("site", site.name())
            .baseUnit(unit)
            .register(registry);
    }

    /** The site statuses, read again once they are older than {@value #SNAPSHOT_SECONDS} seconds. */
    private Map<OfferSite, OfferSiteStatus> statuses() {
        Instant now = clock.instant();
        if (snapshotAt.isBefore(now.minusSeconds(SNAPSHOT_SECONDS))) {
            snapshot = statuses.findAll().stream().collect(Collectors.toMap(OfferSiteStatus::getSite, status -> status));
            snapshotAt = now;
        }
        return snapshot;
    }
}
