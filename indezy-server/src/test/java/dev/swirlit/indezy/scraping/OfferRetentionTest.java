package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import dev.swirlit.indezy.repository.OfferAppearanceRepository;
import dev.swirlit.indezy.repository.OfferChangeRepository;
import dev.swirlit.indezy.repository.OfferSkipRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferRetentionTest {

    private static final Instant NOW = Instant.parse("2026-10-19T04:15:00Z");

    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final JobOfferStateRepository states = mock(JobOfferStateRepository.class);
    private final OfferSkipRepository skips = mock(OfferSkipRepository.class);
    private final OfferChangeRepository changes = mock(OfferChangeRepository.class);
    private final OfferAppearanceRepository appearances = mock(OfferAppearanceRepository.class);
    private final OfferRetention retention = new OfferRetention(offers, states, skips, changes, appearances, new OfferProperties(true, Duration.ofMinutes(5),
        Duration.ofSeconds(2), 10, Duration.ofHours(1), 90, "IndezyBot/test", false, "http://127.0.0.1"),
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void removesOldOffersWithTheirStatesAndSkillsInChunks() {
        List<Long> ids = LongStream.rangeClosed(1, 1200).boxed().toList();
        when(offers.findDisposableIds(NOW.minus(Duration.ofDays(90)), NOW)).thenReturn(ids);

        assertThat(retention.purge()).isEqualTo(1200);

        InOrder order = inOrder(states, changes, offers);
        order.verify(states).deleteByOfferIdIn(ids.subList(0, 500));
        order.verify(changes).deleteByOfferIdIn(ids.subList(0, 500));
        order.verify(offers).deleteSkillsOf(ids.subList(0, 500));
        order.verify(offers).deleteByIdIn(ids.subList(0, 500));
        verify(offers, times(3)).deleteByIdIn(anyCollection());
        verify(offers).deleteByIdIn(ids.subList(1000, 1200));
    }

    @Test
    void doesNothingWhenNothingIsOld() {
        when(offers.findDisposableIds(any(), any())).thenReturn(List.of());

        assertThat(retention.purge()).isZero();
        verify(offers, never()).deleteByIdIn(anyCollection());
        verify(skips).deleteSkippedBefore(NOW.minus(OfferRetention.SKIP_MEMORY));
        verify(appearances).forgetBefore(NOW.minus(OfferRetention.APPEARANCE_MEMORY));
    }
}
