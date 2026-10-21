package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.dto.OfferSearchSuggestionDto;
import dev.swirlit.indezy.dto.OfferSearchSuggestionsDto;
import dev.swirlit.indezy.dto.OfferSearchSuggestionsDto.ProfileField;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.OfferSuggestionDismissal;
import dev.swirlit.indezy.model.SavedOfferSearch;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferSpecifications.Origin;
import dev.swirlit.indezy.repository.OfferMatch;
import dev.swirlit.indezy.repository.OfferSuggestionDismissalRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SavedOfferSearchRepository;
import dev.swirlit.indezy.scraping.GeoLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferSearchSuggestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final OfferSearchService offerSearch = mock(OfferSearchService.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final SavedOfferSearchRepository savedSearches = mock(SavedOfferSearchRepository.class);
    private final OfferSuggestionDismissalRepository dismissals = mock(OfferSuggestionDismissalRepository.class);
    private final MovableClock clock = new MovableClock();
    private final OfferSearchSuggestionService service = new OfferSearchSuggestionService(offerSearch, freelances, projects, offers,
        savedSearches, dismissals, clock);

    @BeforeEach
    void setUp() {
        when(offerSearch.profile(3L)).thenReturn(new OfferMatch.Profile(List.of("Java"), null, null));
        List<Object[]> pipeline = List.of(new Object[] {"Angular, Docker", 500}, new Object[] {"Docker", 700});
        when(projects.findRecentStacksAndRates(eq(3L), any())).thenReturn(pipeline);
    }

    @Test
    @SuppressWarnings("unchecked")
    void suggestsTheSearchesFindingOffersBestFirstLeavingOutTheSavedAndDismissedOnes() {
        // Open, matching and new offers of: Java, Java remote (none), Java hybrid, Docker, Angular.
        when(offers.count(any(Specification.class))).thenReturn(10L, 2L, 1L, 0L, 4L, 3L, 0L, 6L, 0L, 2L, 5L, 0L, 5L);
        OfferSearchRequest docker = search(List.of("Docker"));
        docker.setSort(OfferSearchRequest.Sort.RATE);
        SavedOfferSearch saved = new SavedOfferSearch();
        saved.setFilters(JSON.writeValueAsString(docker));
        when(savedSearches.findByFreelanceIdOrderByNameAsc(3L)).thenReturn(List.of(saved));
        when(dismissals.findKeys(3L)).thenReturn(List.of(OfferSearchSuggestionService.keyOf(search(List.of("Angular")))));

        OfferSearchSuggestionsDto found = service.suggestions(3L);

        // The pipeline's median rate stands for the target the profile does not set.
        assertThat(found.getSuggestions()).extracting(suggestion -> suggestion.getFilters().getSkills())
            .containsExactly(List.of("Java"), List.of("Java"));
        OfferSearchSuggestionDto hybrid = found.getSuggestions().get(0);
        assertThat(hybrid.getFilters().getWorkModes()).containsExactly(WorkMode.HYBRID);
        assertThat(hybrid.getFilters().getRateMin()).isEqualTo(600);
        assertThat(hybrid).extracting(OfferSearchSuggestionDto::getOffers, OfferSearchSuggestionDto::getGoodMatches,
            OfferSearchSuggestionDto::getNewThisWeek, OfferSearchSuggestionDto::getPlace).containsExactly(4L, 3L, 0L, null);
        assertThat(found.getMissing()).containsExactly(ProfileField.POSITION, ProfileField.CITY, ProfileField.TARGET_RATE);

        // Asked again soon with the same profile: the feed is not counted again, but a search saved since is left out.
        when(savedSearches.findByFreelanceIdOrderByNameAsc(3L)).thenReturn(List.of());
        assertThat(service.suggestions(3L).getSuggestions()).hasSize(3);
        verify(offers, times(13)).count(any(Specification.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsAgainOnceTheProfileChangesOrTenMinutesPassed() {
        when(offers.count(any(Specification.class))).thenReturn(1L);
        service.suggestions(3L);
        verify(offers, times(15)).count(any(Specification.class));

        clock.now = NOW.plus(OfferSearchSuggestionService.KEEP_FOR);
        service.suggestions(3L);
        verify(offers, times(30)).count(any(Specification.class));

        when(freelances.findProfilePosition(3L)).thenReturn(Optional.of("Développeur Java"));
        OfferSearchSuggestionsDto withTitle = service.suggestions(3L);
        assertThat(withTitle.getSuggestions().get(0).getFilters().getQ()).isEqualTo("Développeur Java");
        assertThat(withTitle.getMissing()).doesNotContain(ProfileField.POSITION);
        assertThat(withTitle.getSuggestions()).hasSize(OfferSearchSuggestionService.MAX_SUGGESTIONS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void suggestsTheSkillPairOffersAskForMostAndNamesTheCity() {
        OfferMatch.Profile profile = new OfferMatch.Profile(List.of("Java", "Kafka", "SAP"), 650, new Origin(45.76, 4.83));
        when(offerSearch.profile(3L)).thenReturn(profile);
        when(offerSearch.home(3L)).thenReturn(Optional.of(new GeoLocator.Place("Lyon", "69", "84", 45.76, 4.83)));
        // Pairs Java+Kafka (4), Java+SAP (0), Kafka+SAP (9), then every candidate finds one offer.
        List<Long> counts = new ArrayList<>(List.of(4L, 0L, 9L));
        IntStream.range(0, 40).forEach(index -> counts.add(1L));
        when(offers.count(any(Specification.class))).thenReturn(counts.get(0), counts.subList(1, counts.size()).toArray(Long[]::new));

        List<OfferSearchSuggestionDto> suggestions = service.suggestions(3L).getSuggestions();

        assertThat(suggestions).extracting(suggestion -> suggestion.getFilters().getSkills())
            .containsExactly(List.of("Java"), List.of("Kafka"), List.of("Kafka", "SAP"), List.of("Java"), List.of("Java"));
        assertThat(suggestions.get(0).getPlace()).isEqualTo("Lyon");
        assertThat(suggestions.get(2).getPlace()).isNull();
        assertThat(suggestions.get(4).getFilters().getRegions()).containsExactly("84");
        assertThat(suggestions.get(4).getPlace()).isEqualTo("Auvergne-Rhône-Alpes");
        assertThat(suggestions).extracting(suggestion -> suggestion.getFilters().getRateMin()).containsOnly(650);
    }

    @Test
    void tellsSearchesApartByTheirFiltersOnly() {
        OfferSearchRequest first = search(List.of("Java", "Kafka"));
        first.getWorkModes().addAll(List.of(WorkMode.REMOTE, WorkMode.HYBRID));
        OfferSearchRequest second = search(List.of("Kafka", "Java"));
        second.getWorkModes().addAll(List.of(WorkMode.HYBRID, WorkMode.REMOTE));
        second.setSort(OfferSearchRequest.Sort.MATCH);
        second.setPage(3);
        second.setView(OfferSearchRequest.View.SAVED);

        assertThat(OfferSearchSuggestionService.keyOf(first)).isEqualTo(OfferSearchSuggestionService.keyOf(second)).hasSize(32);
        second.setUnseenOnly(true);
        assertThat(OfferSearchSuggestionService.keyOf(first)).isNotEqualTo(OfferSearchSuggestionService.keyOf(second));
        assertThat(OfferSearchSuggestionService.keyOf(new OfferSearchRequest())).isNotEqualTo(OfferSearchSuggestionService.keyOf(first));
    }

    @Test
    void remembersEachDismissalOnceAndTwoHundredAtMost() {
        OfferSearchRequest java = search(List.of("Java"));
        String key = OfferSearchSuggestionService.keyOf(java);
        when(dismissals.existsByFreelanceIdAndSuggestionKey(3L, key)).thenReturn(true);
        service.dismiss(3L, java);
        verify(dismissals, never()).save(any());

        OfferSearchRequest kafka = search(List.of("Kafka"));
        kafka.setView(OfferSearchRequest.View.HIDDEN);
        List<OfferSuggestionDismissal> kept = IntStream.range(0, OfferSearchSuggestionService.MAX_DISMISSALS)
            .mapToObj(index -> new OfferSuggestionDismissal()).toList();
        when(dismissals.findByFreelanceIdOrderByDismissedAtAsc(3L)).thenReturn(kept);
        Freelance workspace = new Freelance();
        when(freelances.getReferenceById(3L)).thenReturn(workspace);

        service.dismiss(3L, kafka);

        verify(dismissals).deleteAll(List.of(kept.get(0)));
        ArgumentCaptor<OfferSuggestionDismissal> saved = ArgumentCaptor.forClass(OfferSuggestionDismissal.class);
        verify(dismissals).save(saved.capture());
        assertThat(saved.getValue().getFreelance()).isSameAs(workspace);
        assertThat(saved.getValue().getSuggestionKey()).isEqualTo(OfferSearchSuggestionService.keyOf(kafka));
        assertThat(saved.getValue().getDismissedAt()).isEqualTo(NOW);
        assertThat(JSON.readValue(saved.getValue().getFilters(), OfferSearchRequest.class).getView()).isEqualTo(OfferSearchRequest.View.FEED);

        when(dismissals.findByFreelanceIdOrderByDismissedAtAsc(3L)).thenReturn(List.of(saved.getValue()));
        assertThat(service.export(3L)).singleElement().satisfies(dismissal -> {
            assertThat(dismissal.getFilters().getSkills()).containsExactly("Kafka");
            assertThat(dismissal.getDismissedAt()).isEqualTo(NOW);
        });
    }

    private static OfferSearchRequest search(List<String> skills) {
        OfferSearchRequest search = new OfferSearchRequest();
        search.setRateMin(600);
        search.getSkills().addAll(skills);
        return search;
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
