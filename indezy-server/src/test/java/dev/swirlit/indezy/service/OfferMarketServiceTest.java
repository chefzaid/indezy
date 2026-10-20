package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferFacetDto;
import dev.swirlit.indezy.dto.OfferMarketDto;
import dev.swirlit.indezy.dto.OfferMarketDto.DayCount;
import dev.swirlit.indezy.dto.OfferMarketDto.RateBin;
import dev.swirlit.indezy.dto.OfferMarketDto.Rates;
import dev.swirlit.indezy.dto.OfferMarketDto.Remote;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.OfferMarketQueries;
import dev.swirlit.indezy.repository.OfferMarketQueries.Figures;
import dev.swirlit.indezy.repository.OfferMatch;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfferMarketServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final OfferMarketQueries queries = mock(OfferMarketQueries.class);
    private final OfferSearchService search = mock(OfferSearchService.class);
    private final OfferMarketService service = new OfferMarketService(queries, search, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void describesTheRatesWithQuartilesAndTheShareThatPaysTheTarget() {
        List<Figures> figures = List.of(rated(500, 600), rated(600, null), rated(null, 700), rated(650, 650), rated(null, null));
        List<Integer> rates = figures.stream().map(OfferMarketService::rate).filter(rate -> rate != null).sorted().toList();

        assertThat(rates).containsExactly(550, 600, 650, 700);
        // Interpolated: 587.5, 625 and 662.5, rounded; the best rates 600, 600, 700 and 650 reach 650 twice out of four.
        assertThat(OfferMarketService.rates(figures, rates, 650)).isEqualTo(new Rates(4, 588, 625, 663, 650, 50));
        assertThat(OfferMarketService.rates(figures, rates, null)).isEqualTo(new Rates(4, 588, 625, 663, null, null));
        assertThat(OfferMarketService.rates(List.of(rated(null, null)), List.of(), 600)).isEqualTo(new Rates(0, null, null, null, 600, null));
    }

    @Test
    void bandsTheUsualRatesAndGathersTheRareOnesInOpenBands() {
        assertThat(OfferMarketService.rateBins(List.of(550, 600, 650, 700))).containsExactly(
            new RateBin(550, 600, 1), new RateBin(600, 650, 1), new RateBin(650, 700, 1), new RateBin(700, null, 1));
        assertThat(OfferMarketService.rateBins(List.of(600))).containsExactly(new RateBin(600, 650, 1));
        assertThat(OfferMarketService.rateBins(List.of())).isEmpty();

        // From 100 to 2,000 euros: bands of 200 keep ten of them.
        List<RateBin> wide = OfferMarketService.rateBins(List.of(100, 2000));
        assertThat(wide).hasSize(11).first().isEqualTo(new RateBin(0, 200, 1));
        assertThat(wide).last().isEqualTo(new RateBin(2000, null, 1));

        List<Integer> withRareOnes = new ArrayList<>(List.of(90));
        IntStream.range(0, 40).forEach(i -> withRareOnes.add(500 + i * 5));
        withRareOnes.add(3000);
        assertThat(OfferMarketService.rateBins(withRareOnes)).first().isEqualTo(new RateBin(null, 500, 1));
        assertThat(OfferMarketService.rateBins(withRareOnes)).last().isEqualTo(new RateBin(700, null, 1));
    }

    @Test
    void splitsTheOffersByWorkModeWithTheHybridOnesRemoteDays() {
        List<Figures> figures = List.of(mode(WorkMode.REMOTE, 5), mode(WorkMode.HYBRID, 2), mode(WorkMode.HYBRID, 3),
            mode(WorkMode.HYBRID, null), mode(WorkMode.ONSITE, null), mode(null, null));

        assertThat(OfferMarketService.remote(figures)).isEqualTo(new Remote(1, 3, 1, 1, 2.5));
        assertThat(OfferMarketService.remote(List.of(mode(WorkMode.REMOTE, null)))).isEqualTo(new Remote(1, 0, 0, 0, null));
    }

    @Test
    void ranksThePublishersUnderTheirMostUsedSpelling() {
        List<Figures> figures = new ArrayList<>(List.of(company("Exemple Conseil"), company("Exemple Conseil"), company("EXEMPLE  conseil"),
            company("Beta"), company("Acme"), company(null), company("  ")));

        assertThat(OfferMarketService.intermediaries(figures)).containsExactly(
            new OfferFacetDto("Exemple Conseil", 3), new OfferFacetDto("Acme", 1), new OfferFacetDto("Beta", 1));
        IntStream.range(0, 12).forEach(i -> figures.add(company("ESN " + i)));
        assertThat(OfferMarketService.intermediaries(figures)).hasSize(OfferMarketService.TOP);
    }

    @Test
    void countsTheOffersPublishedEachOfTheLastThirtyParisDays() {
        List<Figures> figures = List.of(published("2026-10-18T22:30:00Z"), published("2026-10-19T09:00:00Z"),
            published("2026-09-20T10:00:00Z"), published("2026-09-19T10:00:00Z"), published(null));

        List<DayCount> days = OfferMarketService.perDay(figures, NOW);

        assertThat(days).hasSize(OfferMarketService.DAYS);
        // 22:30 UTC on the 18th is past midnight in Paris.
        assertThat(days.getLast()).isEqualTo(new DayCount(LocalDate.of(2026, 10, 19), 2));
        assertThat(days.getFirst()).isEqualTo(new DayCount(LocalDate.of(2026, 9, 20), 1));
        assertThat(days.stream().mapToLong(DayCount::offers).sum()).isEqualTo(3);
    }

    @Test
    void snapshotsWhatTheSearchFindsForTheWorkspace() {
        OfferSearchRequest request = new OfferSearchRequest();
        when(search.profileFor(3L, request)).thenReturn(new OfferMatch.Profile(List.of("Java"), 600, null));
        when(queries.figures(any())).thenReturn(List.of(
            new Figures(550, 650, WorkMode.HYBRID, 2, "Exemple Conseil", Instant.parse("2026-10-19T08:00:00Z")),
            new Figures(null, null, WorkMode.REMOTE, null, null, Instant.parse("2026-10-10T08:00:00Z"))));
        when(queries.topSkills(any(), eq(OfferMarketService.TOP))).thenReturn(List.of(new OfferFacetDto("Java", 2)));

        OfferMarketDto market = service.snapshot(3L, request);

        assertThat(market.offers()).isEqualTo(2);
        assertThat(market.rates()).isEqualTo(new Rates(1, 600, 600, 600, 600, 100));
        assertThat(market.rateBins()).containsExactly(new RateBin(600, 650, 1));
        assertThat(market.remote()).isEqualTo(new Remote(1, 1, 0, 0, 2.0));
        assertThat(market.skills()).containsExactly(new OfferFacetDto("Java", 2));
        assertThat(market.intermediaries()).containsExactly(new OfferFacetDto("Exemple Conseil", 1));
        assertThat(market.perDay().getLast().offers()).isEqualTo(1);
    }

    private static Figures rated(Integer rateMin, Integer rateMax) {
        return new Figures(rateMin, rateMax, null, null, null, NOW);
    }

    private static Figures mode(WorkMode workMode, Integer remoteDays) {
        return new Figures(null, null, workMode, remoteDays, null, NOW);
    }

    private static Figures company(String company) {
        return new Figures(null, null, null, null, company, NOW);
    }

    private static Figures published(String publishedAt) {
        return new Figures(null, null, null, null, null, publishedAt == null ? null : Instant.parse(publishedAt));
    }
}
