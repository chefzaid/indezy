package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.OfferSiteStatusRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferSiteRunsTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final OfferSiteStatusRepository statuses = mock(OfferSiteStatusRepository.class);
    private final OfferMetrics metrics = mock(OfferMetrics.class);
    private final OfferSiteRuns runs = new OfferSiteRuns(statuses, new OfferProperties(true, Duration.ofMinutes(5),
        Duration.ofSeconds(2), 10, Duration.ofHours(1), 90, "IndezyBot/test", false, "http://127.0.0.1"), metrics);

    @Test
    void createsAStatusOnFirstClaimAndWinsWhenTheUpdateDoes() {
        when(statuses.existsById(OfferSite.YEETS)).thenReturn(false);
        when(statuses.claim(OfferSite.YEETS, NOW, NOW.minus(Duration.ofHours(1)))).thenReturn(1);

        assertThat(runs.claim(OfferSite.YEETS, NOW)).isTrue();

        ArgumentCaptor<OfferSiteStatus> created = ArgumentCaptor.forClass(OfferSiteStatus.class);
        verify(statuses).saveAndFlush(created.capture());
        assertThat(created.getValue().getNextRunAt()).isEqualTo(NOW);
    }

    @Test
    void losesASiteThatIsNotDueOrHeld() {
        when(statuses.existsById(OfferSite.YEETS)).thenReturn(true);
        when(statuses.claim(any(), any(), any())).thenReturn(0);

        assertThat(runs.claim(OfferSite.YEETS, NOW)).isFalse();
        verify(statuses, never()).saveAndFlush(any());
    }

    @Test
    void acceptsARefreshWhenTheUpdateDoesAndTellsWhenTheNextOneIsPossible() {
        when(statuses.existsById(OfferSite.YEETS)).thenReturn(false);
        when(statuses.requestRefresh(OfferSite.YEETS, NOW, NOW.minus(Duration.ofMinutes(15)), NOW.minus(Duration.ofHours(1)))).thenReturn(1);

        assertThat(runs.requestRefresh(OfferSite.YEETS, NOW)).isTrue();
        verify(statuses).saveAndFlush(any());

        OfferSiteStatus status = new OfferSiteStatus(OfferSite.YEETS, NOW);
        assertThat(OfferSiteRuns.refreshAvailableAt(status)).isNull();
        status.setRefreshRequestedAt(NOW);
        assertThat(OfferSiteRuns.refreshAvailableAt(status)).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        status.setLastRunAt(NOW.minusSeconds(60));
        assertThat(OfferSiteRuns.refreshAvailableAt(status)).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        status.setLastRunAt(NOW.plusSeconds(60));
        assertThat(OfferSiteRuns.refreshAvailableAt(status)).isEqualTo(NOW.plus(Duration.ofMinutes(16)));
    }

    @Test
    void schedulesTheNextRunAfterASuccess() {
        OfferSiteStatus status = new OfferSiteStatus(OfferSite.FREE_WORK, NOW);
        status.setRunningSince(NOW);
        status.setConsecutiveFailures(3);
        status.setLastError("boom");
        when(statuses.findById(OfferSite.FREE_WORK)).thenReturn(Optional.of(status));

        runs.succeeded(OfferSite.FREE_WORK, NOW, 120, 7);

        verify(metrics).runEnded(OfferSite.FREE_WORK, true, 120, 7, Duration.ZERO);

        assertThat(status.getRunningSince()).isNull();
        assertThat(status.getLastSuccessAt()).isEqualTo(NOW);
        assertThat(status.getLastError()).isNull();
        assertThat(status.getLastFound()).isEqualTo(120);
        assertThat(status.getLastNew()).isEqualTo(7);
        assertThat(status.getConsecutiveFailures()).isZero();
        assertThat(status.getNextRunAt()).isEqualTo(NOW.plus(OfferSite.FREE_WORK.getRefreshInterval()));
        verify(statuses).save(status);
    }

    @Test
    void backsOffAfterFailuresUpToADay() {
        OfferSiteStatus status = new OfferSiteStatus(OfferSite.LEHIBOU, NOW);
        when(statuses.findById(OfferSite.LEHIBOU)).thenReturn(Optional.of(status));

        runs.failed(OfferSite.LEHIBOU, NOW, "x".repeat(800));

        verify(metrics).runEnded(OfferSite.LEHIBOU, false, 0, 0, null);

        assertThat(status.getConsecutiveFailures()).isEqualTo(1);
        assertThat(status.getLastError()).hasSize(500);
        assertThat(status.getNextRunAt()).isEqualTo(NOW.plus(OfferSite.LEHIBOU.getRefreshInterval().multipliedBy(2)));

        status.setConsecutiveFailures(20);
        runs.failed(OfferSite.LEHIBOU, NOW, null);
        assertThat(status.getNextRunAt()).isEqualTo(NOW.plus(OfferSiteRuns.MAX_BACKOFF));
        assertThat(status.getLastError()).isNull();
    }

    @Test
    void recordsARunOfASiteWithoutStatus() {
        when(statuses.findById(any())).thenReturn(Optional.empty());

        runs.succeeded(OfferSite.YEETS, NOW, 1, 1);
        runs.failed(OfferSite.YEETS, NOW, "boom");

        verify(statuses, org.mockito.Mockito.times(2)).save(any(OfferSiteStatus.class));
    }
}
