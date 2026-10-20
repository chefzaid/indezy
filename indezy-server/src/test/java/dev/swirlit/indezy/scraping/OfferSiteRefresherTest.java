package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.OfferSiteStatusRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferSiteRefresherTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final OfferSiteRuns runs = mock(OfferSiteRuns.class);
    private final OfferSiteStatusRepository statuses = mock(OfferSiteStatusRepository.class);
    private final OfferCollector collector = mock(OfferCollector.class);
    private final List<Runnable> tasks = new ArrayList<>();

    @Test
    void readsTheSiteInTheBackgroundOnceItIsClaimed() {
        when(runs.requestRefresh(OfferSite.YEETS, NOW)).thenReturn(true);
        when(runs.claim(OfferSite.YEETS, NOW)).thenReturn(true);

        assertThat(refresher(true).refresh(OfferSite.YEETS)).isEqualTo(NOW.plus(Duration.ofMinutes(15)));

        verify(collector, never()).collect(any());
        assertThat(tasks).hasSize(1);
        tasks.get(0).run();
        verify(collector).collect(OfferSite.YEETS);
    }

    @Test
    void leavesTheSiteToARunThatClaimedItFirst() {
        when(runs.requestRefresh(OfferSite.YEETS, NOW)).thenReturn(true);
        when(runs.claim(OfferSite.YEETS, NOW)).thenReturn(false);

        refresher(true).refresh(OfferSite.YEETS);
        tasks.get(0).run();

        verify(collector, never()).collect(any());
    }

    @Test
    void refusesARequestTooSoonWithTheTimeToWait() {
        OfferSiteStatus status = new OfferSiteStatus(OfferSite.YEETS, NOW);
        status.setLastRunAt(NOW.minus(Duration.ofMinutes(5)));
        when(statuses.findById(OfferSite.YEETS)).thenReturn(Optional.of(status));

        assertThatThrownBy(() -> refresher(true).refresh(OfferSite.YEETS))
            .isInstanceOfSatisfying(TooManyRequestsException.class, refused -> {
                assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(refused.getReason()).startsWith("Yeets is read on request at most once every 15 minutes");
                assertThat(refused.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("600");
            });
        // Refused while a run holds it, which was read long ago: it is worth asking again soon.
        status.setLastRunAt(NOW.minus(Duration.ofHours(2)));
        assertThatThrownBy(() -> refresher(true).refresh(OfferSite.YEETS))
            .isInstanceOfSatisfying(TooManyRequestsException.class,
                refused -> assertThat(refused.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60"));
        assertThat(tasks).isEmpty();
    }

    @Test
    void refusesWhenTheCollectionIsOff() {
        assertThatThrownBy(() -> refresher(false).refresh(OfferSite.YEETS))
            .isInstanceOf(InvalidStateException.class)
            .hasMessage("Offer collection is off on this server");
        verify(runs, never()).requestRefresh(any(), any());
    }

    private OfferSiteRefresher refresher(boolean collectionEnabled) {
        OfferProperties properties = new OfferProperties(collectionEnabled, Duration.ofMinutes(5), Duration.ofSeconds(2), 10,
            Duration.ofHours(1), 90, "IndezyBot/test", false, "http://127.0.0.1");
        return new OfferSiteRefresher(runs, statuses, collector, properties, tasks::add, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
