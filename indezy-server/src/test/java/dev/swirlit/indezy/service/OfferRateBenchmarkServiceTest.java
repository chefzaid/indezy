package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferRateMarketDto;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferRateBenchmark;
import dev.swirlit.indezy.model.OfferRateHistory;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferRateBenchmarkRepository;
import dev.swirlit.indezy.repository.OfferRateHistoryRepository;
import org.junit.jupiter.api.Test;
import org.assertj.core.groups.Tuple;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferRateBenchmarkServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T02:45:00Z");

    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final OfferRateBenchmarkRepository benchmarks = mock(OfferRateBenchmarkRepository.class);
    private final OfferRateHistoryRepository history = mock(OfferRateHistoryRepository.class);
    private final MovableClock clock = new MovableClock();
    private final OfferRateBenchmarkService service = new OfferRateBenchmarkService(offers, benchmarks, history, clock);

    @Test
    @SuppressWarnings("unchecked")
    void takesTheMedianOfEachSkillInEachRegionAndInFranceOncePerMission() {
        List<Object[]> rows = new ArrayList<>();
        // Ten Java missions in Île-de-France from 500 to 950, one of them published twice.
        IntStream.range(0, 10).forEach(index -> rows.add(new Object[] {(long) index, index == 0 ? 90L : null, "11", 500 + index * 50, null,
            index % 2 == 0 ? "Java" : "java "}));
        rows.add(new Object[] {50L, 90L, "11", 2_000, null, "Java"});
        // One more in Auvergne-Rhône-Alpes, a range read as its middle (500), and one without a region: twelve in all of France,
        // whose middle two are 650 and 700.
        rows.add(new Object[] {60L, null, "84", 400, 600, "Java"});
        rows.add(new Object[] {61L, null, null, 300, null, "JAVA"});
        // Too few Kafka missions, and a skill without words.
        rows.add(new Object[] {70L, null, "11", 700, null, "Kafka"});
        rows.add(new Object[] {71L, null, "11", 700, null, "  "});
        when(offers.findRatedMainSkills(NOW)).thenReturn(rows);

        assertThat(service.refresh()).isEqualTo(2);

        var order = inOrder(benchmarks);
        order.verify(benchmarks).deleteAllInBatch();
        ArgumentCaptor<List<OfferRateBenchmark>> saved = ArgumentCaptor.forClass(List.class);
        order.verify(benchmarks).saveAll(saved.capture());
        List<OfferRateBenchmark> kept = saved.getValue().stream().sorted(Comparator.comparing(OfferRateBenchmark::getRegionCode)).toList();
        assertThat(kept).extracting(OfferRateBenchmark::getRegionCode, OfferRateBenchmark::getSkillKey, OfferRateBenchmark::getSkill,
                OfferRateBenchmark::getMedianRate, OfferRateBenchmark::getOffers)
            .containsExactly(Tuple.tuple("11", "java", "Java", 725, 10),
                Tuple.tuple(OfferRateBenchmark.FRANCE, "java", "Java", 675, 12));
        assertThat(kept).extracting(OfferRateBenchmark::getComputedAt).containsOnly(NOW);
        // The night joins the history, replacing an earlier run of the same day; nights older than 400 days go.
        LocalDate today = LocalDate.of(2026, 10, 21);
        var nights = inOrder(history);
        nights.verify(history).forgetDay(today);
        nights.verify(history).forgetBefore(today.minusDays(OfferRateBenchmarkService.HISTORY_DAYS));
        ArgumentCaptor<List<OfferRateHistory>> remembered = ArgumentCaptor.forClass(List.class);
        nights.verify(history).saveAll(remembered.capture());
        assertThat(remembered.getValue()).extracting(OfferRateHistory::getDay, OfferRateHistory::getRegionCode, OfferRateHistory::getMedianRate)
            .containsExactlyInAnyOrder(Tuple.tuple(today, "11", 725), Tuple.tuple(today, OfferRateBenchmark.FRANCE, 675));
    }

    @Test
    void comparesAnOffersRateWithItsSkillInItsRegionElseInFrance() {
        when(benchmarks.findAll()).thenReturn(List.of(benchmark("java", "Java", "11", 600, 34), benchmark("java", "Java", "FR", 550, 120)));

        assertThat(service.compare(offer(600, 744, "11"), List.of("JAVA", "Kafka")))
            .isEqualTo(new OfferRateMarketDto(12, 600, 34, "Java", "Île-de-France"));
        assertThat(service.compare(offer(500, null, "84"), List.of("Java")))
            .isEqualTo(new OfferRateMarketDto(-9, 550, 120, "Java", null));
        assertThat(service.compare(offer(null, 550, null), List.of("Java")).percent()).isZero();
        assertThat(service.compare(offer(null, null, "11"), List.of("Java"))).isNull();
        assertThat(service.compare(offer(600, null, "11"), List.of())).isNull();
        assertThat(service.compare(offer(600, null, "11"), null)).isNull();
        assertThat(service.compare(offer(600, null, "11"), List.of("Kafka"))).isNull();
        // Read once, kept ten minutes.
        verify(benchmarks).findAll();
        clock.now = NOW.plus(OfferRateBenchmarkService.KEEP_FOR);
        service.compare(offer(600, null, "11"), List.of("Java"));
        verify(benchmarks, times(2)).findAll();
    }

    @Test
    void readsTheMiddleOfARangeAndTheMedianOfRates() {
        assertThat(OfferRateBenchmarkService.middle(500, 651)).isEqualTo(576);
        assertThat(OfferRateBenchmarkService.middle(null, 600)).isEqualTo(600);
        assertThat(OfferRateBenchmarkService.middle(450, null)).isEqualTo(450);
        assertThat(OfferRateBenchmarkService.middle(0, null)).isNull();
        assertThat(OfferRateBenchmarkService.middle(null, null)).isNull();
        assertThat(OfferRateBenchmarkService.median(List.of(700, 500, 600))).isEqualTo(600);
        assertThat(OfferRateBenchmarkService.median(List.of(500, 651))).isEqualTo(576);
    }

    @Test
    void tellsWhetherAnyMedianWasComputed() {
        when(benchmarks.count()).thenReturn(0L, 3L);

        assertThat(service.isEmpty()).isTrue();
        assertThat(service.isEmpty()).isFalse();
    }

    private static OfferRateBenchmark benchmark(String key, String skill, String region, int median, int count) {
        OfferRateBenchmark benchmark = new OfferRateBenchmark();
        benchmark.setSkillKey(key);
        benchmark.setSkill(skill);
        benchmark.setRegionCode(region);
        benchmark.setMedianRate(median);
        benchmark.setOffers(count);
        return benchmark;
    }

    private static JobOffer offer(Integer rateMin, Integer rateMax, String region) {
        JobOffer offer = new JobOffer();
        offer.setRateMin(rateMin);
        offer.setRateMax(rateMax);
        offer.setRegionCode(region);
        return offer;
    }

    /** A clock the test moves forward. */
    private static final class MovableClock extends Clock {
        private Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
