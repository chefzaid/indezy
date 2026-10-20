package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.OfferSiteStatusRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferMetricsTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OfferSiteStatusRepository statuses = mock(OfferSiteStatusRepository.class);
    private final OfferMetrics metrics = new OfferMetrics(registry, statuses, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void countsTheRunsTheirOffersAndTheirDuration() {
        metrics.runEnded(OfferSite.YEETS, true, 36, 4, Duration.ofSeconds(12));
        metrics.runEnded(OfferSite.YEETS, false, 0, 0, null);
        metrics.geocodingTimedOut();

        assertThat(registry.get("indezy.offers.runs").tags("site", "YEETS", "outcome", "success").counter().count()).isEqualTo(1);
        assertThat(registry.get("indezy.offers.runs").tags("site", "YEETS", "outcome", "failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("indezy.offers.found").tag("site", "YEETS").counter().count()).isEqualTo(36);
        assertThat(registry.get("indezy.offers.new").tag("site", "YEETS").counter().count()).isEqualTo(4);
        assertThat(registry.get("indezy.offers.run.duration").tag("site", "YEETS").timer().count()).isEqualTo(1);
        assertThat(registry.get("indezy.offers.geocoding.timeouts").counter().count()).isEqualTo(1);
    }

    @Test
    void readsEachSitesHealthFromTheSharedStatusesAtMostOnceAMinute() {
        OfferSiteStatus yeets = new OfferSiteStatus(OfferSite.YEETS, NOW);
        yeets.setConsecutiveFailures(3);
        yeets.setLastFound(0);
        yeets.setLastSuccessAt(NOW.minus(Duration.ofHours(2)));
        when(statuses.findAll()).thenReturn(List.of(yeets));

        assertThat(registry.get("indezy.offers.site.consecutive.failures").tag("site", "YEETS").gauge().value()).isEqualTo(3);
        assertThat(registry.get("indezy.offers.site.last.found").tag("site", "YEETS").gauge().value()).isZero();
        assertThat(registry.get("indezy.offers.site.last.success.age").tag("site", "YEETS").gauge().value()).isEqualTo(7200);
        assertThat(registry.get("indezy.offers.site.last.found").tag("site", "KOINO").gauge().value()).isNaN();
        verify(statuses, times(1)).findAll();
    }
}
