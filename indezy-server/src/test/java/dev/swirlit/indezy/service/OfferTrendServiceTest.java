package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferTrendDto;
import dev.swirlit.indezy.model.OfferRateHistory;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.OfferRateHistoryRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferTrendServiceTest {

    /** 21 October in Paris. */
    private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    private final OfferRateHistoryRepository history = mock(OfferRateHistoryRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final OfferTrendService service = new OfferTrendService(history, freelances, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void comparesTheLatestNightWithThirtyNinetyAndThreeHundredSixtyFiveDaysBefore() {
        when(freelances.findProfileSkills(3L)).thenReturn(List.of("Java", "JAVA ", "Kafka"));
        // A year before the latest night is missing (the nearest night is eight days earlier), 90 days before is five days early.
        when(history.findBySkillKeyAndRegionCodeAndDayGreaterThanEqualOrderByDay("java", "11", TODAY.minusDays(372))).thenReturn(List.of(
            night(TODAY.minusDays(374), 500, 40), night(TODAY.minusDays(96), 560, 50), night(TODAY.minusDays(31), 600, 40),
            night(TODAY.minusDays(1), 630, 30)));

        OfferTrendDto trends = service.trends(3L, null, "11");

        assertThat(trends.skills()).containsExactly("Java", "Kafka");
        assertThat(trends.skill()).isEqualTo("Java");
        assertThat(trends.regionName()).isEqualTo("Île-de-France");
        assertThat(trends.latest()).isEqualTo(new OfferTrendDto.Night(TODAY.minusDays(1), 630, 30));
        assertThat(trends.changes()).containsExactly(new OfferTrendDto.Change(30, 5, -25), new OfferTrendDto.Change(90, 13, -40),
            new OfferTrendDto.Change(365, null, null));
        assertThat(trends.nights()).hasSize(4);
    }

    @Test
    void readsAllOfFranceWithoutARegionAndASkillChosen() {
        when(history.findBySkillKeyAndRegionCodeAndDayGreaterThanEqualOrderByDay("kafka", "FR", TODAY.minusDays(372)))
            .thenReturn(List.of(night(TODAY, 650, 0)));

        OfferTrendDto trends = service.trends(3L, " Kafka ", "99");

        assertThat(trends.regionName()).isNull();
        assertThat(trends.skills()).isEmpty();
        assertThat(trends.latest().median()).isEqualTo(650);
        assertThat(trends.changes()).extracting(OfferTrendDto.Change::medianPercent).containsOnlyNulls();
    }

    @Test
    void saysNothingWithoutSkillOrHistory() {
        OfferTrendDto none = service.trends(3L, null, null);
        assertThat(none.skill()).isNull();
        assertThat(none.latest()).isNull();
        verify(history, never()).findBySkillKeyAndRegionCodeAndDayGreaterThanEqualOrderByDay(anyString(), anyString(), any());

        OfferTrendDto empty = service.trends(3L, "Cobol", null);
        assertThat(empty.skill()).isEqualTo("Cobol");
        assertThat(empty.latest()).isNull();
        assertThat(empty.changes()).isNull();
    }

    private static OfferRateHistory night(LocalDate day, int median, int offers) {
        OfferRateHistory night = new OfferRateHistory();
        night.setDay(day);
        night.setSkill("Java");
        night.setMedianRate(median);
        night.setOffers(offers);
        return night;
    }
}
