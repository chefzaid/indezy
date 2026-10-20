package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.dto.SavedOfferSearchDto;
import dev.swirlit.indezy.dto.SavedOfferSearchRequest;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.SavedOfferSearch;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.SavedOfferSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SavedOfferSearchServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final SavedOfferSearchRepository searches = mock(SavedOfferSearchRepository.class);
    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final OfferSearchService offerSearch = mock(OfferSearchService.class);
    private final SavedOfferSearchService service = new SavedOfferSearchService(searches, offers, freelances, offerSearch,
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void savesTheFiltersUnderATrimmedNameAndKeepsEveryFilter() {
        Freelance freelance = new Freelance();
        freelance.setId(3L);
        when(freelances.findById(3L)).thenReturn(Optional.of(freelance));
        when(searches.save(any(SavedOfferSearch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OfferSearchRequest filters = new OfferSearchRequest();
        filters.setWorkModes(List.of(WorkMode.REMOTE));
        filters.setStartBefore(LocalDate.of(2026, 12, 1));
        filters.setSort(OfferSearchRequest.Sort.RATE);

        SavedOfferSearchDto saved = service.create(3L, new SavedOfferSearchRequest("  Remote  ", filters));

        assertThat(saved.getName()).isEqualTo("Remote");
        assertThat(saved.getLastOpenedAt()).isEqualTo(NOW);
        assertThat(saved.getFilters().getWorkModes()).containsExactly(WorkMode.REMOTE);
        assertThat(saved.getFilters().getStartBefore()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(saved.getFilters().getSort()).isEqualTo(OfferSearchRequest.Sort.RATE);
    }

    @Test
    void refusesMoreThanTwentySearchesAndUnknownWorkspaces() {
        when(searches.countByFreelanceId(3L)).thenReturn((long) SavedOfferSearchService.MAX_SEARCHES);
        when(freelances.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(3L, new SavedOfferSearchRequest("Java", new OfferSearchRequest())))
            .isInstanceOf(InvalidStateException.class).hasMessageContaining("20");
        assertThatThrownBy(() -> service.create(4L, new SavedOfferSearchRequest("Java", new OfferSearchRequest())))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(searches, never()).save(any());
    }

    @Test
    void opensDeletesAndExportsOnlyTheWorkspacesOwnSearches() {
        SavedOfferSearch search = new SavedOfferSearch();
        search.setId(9L);
        search.setName("Java");
        search.setFilters("{\"q\":\"java\"}");
        search.setLastOpenedAt(NOW.minusSeconds(3600));
        when(searches.findByIdAndFreelanceId(9L, 3L)).thenReturn(Optional.of(search));
        when(searches.findByFreelanceIdOrderByNameAsc(3L)).thenReturn(List.of(search));

        assertThat(service.open(3L, 9L).getLastOpenedAt()).isEqualTo(NOW);
        assertThat(service.export(3L)).singleElement().satisfies(exported -> assertThat(exported.getFilters().getQ()).isEqualTo("java"));
        service.delete(3L, 9L);
        verify(searches).delete(search);
        assertThatThrownBy(() -> service.open(4L, 9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsNothingForAWorkspaceWithoutSearches() {
        assertThat(service.alerts(3L).newOffers()).isZero();
        verify(offers, never()).count(any(Specification.class));
    }
}
