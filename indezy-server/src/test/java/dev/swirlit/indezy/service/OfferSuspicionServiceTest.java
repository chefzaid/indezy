package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferRateMarketDto;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.OfferSuspicion;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferAppearanceRepository;
import dev.swirlit.indezy.repository.OfferChangeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferSuspicionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T15:00:00Z");

    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final OfferRateBenchmarkService benchmarks = mock(OfferRateBenchmarkService.class);
    private final OfferAppearanceRepository appearances = mock(OfferAppearanceRepository.class);
    private final OfferChangeRepository offerChanges = mock(OfferChangeRepository.class);
    private final OfferSuspicionService service = new OfferSuspicionService(offers, benchmarks, appearances, offerChanges,
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @SuppressWarnings("unchecked")
    void flagsEachOfferByTheRulesAndStoresOnlyWhatChanged() {
        List<Object[]> rows = new ArrayList<>(List.of(
            // id, publisher, opening, rate min, rate max, region, stored reasons, no description
            row(1L, null, null, null, null, null, null, true),
            row(2L, "digistrat conseil", null, null, null, null, null, false),
            row(3L, "acme", "same", 600, null, "11", null, false),
            row(4L, "globex", "same", 3_000, null, "11", OfferSuspicion.SAME_TEXT_MANY_PUBLISHERS.bit(), false),
            row(5L, "initech", "same", null, null, null, null, false),
            row(6L, "umbrella", "same", null, null, null, null, false),
            row(7L, "acme", "other", 600, null, "11", OfferSuspicion.NO_PUBLISHER_NO_DESCRIPTION.bit(), false),
            row(8L, null, null, null, null, null, null, false)));
        when(offers.findSuspicionInputs(NOW)).thenReturn(rows);
        when(offers.findMainSkills(NOW)).thenReturn(List.<Object[]>of(new Object[] {4L, "Java"}, new Object[] {3L, "Java"}));
        when(benchmarks.compare(3_000, null, "11", "Java")).thenReturn(new OfferRateMarketDto(400, 600, 34, "Java", "Île-de-France"));
        when(benchmarks.compare(600, null, "11", "Java")).thenReturn(new OfferRateMarketDto(0, 600, 34, "Java", "Île-de-France"));

        assertThat(service.refresh()).isEqualTo(7);

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(offers).setSuspicion(eq(OfferSuspicion.NO_PUBLISHER_NO_DESCRIPTION.bit()), ids.capture());
        assertThat(ids.getValue()).containsExactly(1L);
        verify(offers).setSuspicion(eq(OfferSuspicion.KNOWN_FAKE_PUBLISHER.bit()), ids.capture());
        assertThat(ids.getValue()).containsExactly(2L);
        verify(offers).setSuspicion(eq(OfferSuspicion.SAME_TEXT_MANY_PUBLISHERS.bit()), ids.capture());
        // Four publishers post "same"; offer 4 already had that reason and only gains the rate.
        assertThat(ids.getValue()).containsExactlyInAnyOrder(3L, 5L, 6L);
        verify(offers).setSuspicion(eq(OfferSuspicion.SAME_TEXT_MANY_PUBLISHERS.bit() | OfferSuspicion.RATE_OUTSIDE_MARKET.bit()), ids.capture());
        assertThat(ids.getValue()).containsExactly(4L);
        // Offer 7 is no longer suspicious; offer 8 never was.
        verify(offers).setSuspicion(isNull(), ids.capture());
        assertThat(ids.getValue()).containsExactly(7L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void flagsRatesFarBelowOrAboveTheMarketOnly() {
        when(offers.findSuspicionInputs(NOW)).thenReturn(List.of(row(1L, "acme", null, 200, null, "11", null, false),
            row(2L, "acme", null, 1_400, null, "11", null, false), row(3L, "acme", null, 1_500, null, "11", null, false)));
        when(offers.findMainSkills(NOW)).thenReturn(List.<Object[]>of(new Object[] {1L, "Java"}, new Object[] {2L, "Java"},
            new Object[] {3L, "Java"}));
        when(benchmarks.compare(200, null, "11", "Java")).thenReturn(new OfferRateMarketDto(-60, 500, 34, "Java", null));
        when(benchmarks.compare(1_400, null, "11", "Java")).thenReturn(new OfferRateMarketDto(149, 562, 34, "Java", null));
        when(benchmarks.compare(1_500, null, "11", "Java")).thenReturn(new OfferRateMarketDto(150, 600, 34, "Java", null));

        service.refresh();

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(offers).setSuspicion(eq(OfferSuspicion.RATE_OUTSIDE_MARKET.bit()), ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void storesThousandsOfChangesFiveHundredAtATime() {
        List<Object[]> rows = new ArrayList<>();
        IntStream.range(0, 1_200).forEach(index -> rows.add(row((long) index, null, null, null, null, null, null, true)));
        when(offers.findSuspicionInputs(NOW)).thenReturn(rows);

        assertThat(service.refresh()).isEqualTo(1_200);

        verify(offers, times(3)).setSuspicion(eq(OfferSuspicion.NO_PUBLISHER_NO_DESCRIPTION.bit()), anyCollection());
    }

    @Test
    void changesNothingWhenNothingChanged() {
        when(offers.findSuspicionInputs(NOW)).thenReturn(List.<Object[]>of(row(1L, "acme", null, null, null, null, null, false)));

        assertThat(service.refresh()).isZero();

        verify(offers, never()).setSuspicion(any(), anyCollection());
    }

    @Test
    void keepsEachReasonAsABit() {
        assertThat(OfferSuspicion.mask(List.of())).isNull();
        Integer mask = OfferSuspicion.mask(List.of(OfferSuspicion.KNOWN_FAKE_PUBLISHER, OfferSuspicion.NO_PUBLISHER_NO_DESCRIPTION));
        assertThat(mask).isEqualTo(9);
        assertThat(OfferSuspicion.of(mask)).containsExactly(OfferSuspicion.NO_PUBLISHER_NO_DESCRIPTION, OfferSuspicion.KNOWN_FAKE_PUBLISHER);
        assertThat(OfferSuspicion.of(0)).isNull();
        assertThat(OfferSuspicion.of(null)).isNull();
    }

    /** An offer as {@code findSuspicionInputs} reads it: id, publisher, opening, rates, region, stored reasons, no description. */
    @Test
    void flagsAMissionRepostedOftenWithoutAChange() {
        when(offers.findSuspicionInputs(NOW)).thenReturn(List.of(row(1L, "acme", null, null, null, null, null, false),
            row(2L, "acme", null, null, null, null, null, false), row(3L, "globex", null, null, null, null, null, false)));
        when(appearances.findRepostedSince(NOW.minus(OfferSuspicionService.REPOSTED_WITHIN), OfferSuspicionService.REPOSTED_TIMES))
            .thenReturn(List.<Object[]>of(new Object[] {OfferSite.FREE_WORK, "acme-lead-java"}));
        when(offers.findOpenPublisherKeys(NOW)).thenReturn(List.of(new Object[] {1L, OfferSite.FREE_WORK, "acme-lead-java"},
            new Object[] {2L, OfferSite.FREE_WORK, "acme-lead-java"}, new Object[] {3L, OfferSite.COLLECTIVE, "acme-lead-java"}));
        // Offer 2's rate changed once: a mission that moves is not a harvesting listing.
        when(offerChanges.findChangedOfferIds()).thenReturn(List.of(2L));

        assertThat(service.refresh()).isEqualTo(1);
        verify(offers).setSuspicion(OfferSuspicion.REPOSTED_UNCHANGED.bit(), List.of(1L));
    }

    private static Object[] row(Object... columns) {
        return columns;
    }
}