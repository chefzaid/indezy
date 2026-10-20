package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.OfferLeftOutSite;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.SourceType;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobBoardSourcesTest {

    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final SourceRepository sources = mock(SourceRepository.class);
    private final JobBoardSources jobBoardSources = new JobBoardSources(freelances, sources);

    @Test
    @SuppressWarnings("unchecked")
    void givesTheWorkspaceEveryBoardItHasNoSourceFor() {
        Freelance freelance = new Freelance();
        Source mine = new Source();
        mine.setName("free-work");
        when(freelances.claimJobBoardSources(3L)).thenReturn(1);
        when(freelances.getReferenceById(3L)).thenReturn(freelance);
        when(sources.findByFreelanceId(3L)).thenReturn(List.of(mine));

        int added = jobBoardSources.seed(3L);

        int boards = OfferSite.values().length + OfferLeftOutSite.values().length;
        assertThat(added).isEqualTo(boards - 1);
        ArgumentCaptor<List<Source>> saved = ArgumentCaptor.forClass(List.class);
        verify(sources).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Source::getName).doesNotContain("Free-Work").contains("Collective", "Malt", "LinkedIn");
        assertThat(saved.getValue()).filteredOn(source -> source.getName().equals("LinkedIn"))
            .extracting(Source::getType, Source::getIsListing, Source::getLink)
            .containsExactly(tuple(SourceType.SOCIAL_MEDIA, true, "https://www.linkedin.com"));
        assertThat(saved.getValue()).filteredOn(source -> source.getName().equals("Malt"))
            .extracting(Source::getType, Source::getIsListing).containsExactly(tuple(SourceType.JOB_BOARD, false));
        assertThat(saved.getValue()).allSatisfy(source -> assertThat(source.getFreelance()).isSameAs(freelance));
    }

    @Test
    void givesThemOnceOnly() {
        when(freelances.claimJobBoardSources(3L)).thenReturn(0);

        assertThat(jobBoardSources.seed(3L)).isZero();
        verify(sources, never()).saveAll(any());
    }

    @Test
    void namesEveryBoardOnceWithItsHomeLink() {
        assertThat(JobBoardSources.catalog()).extracting(JobBoardSources.Board::name).doesNotHaveDuplicates()
            .hasSize(OfferSite.values().length + OfferLeftOutSite.values().length);
        assertThat(JobBoardSources.catalog()).allSatisfy(board -> assertThat(board.link()).startsWith("https://"));
    }

    @Test
    void seedsTheWorkspacesCreatedBeforeOnStartup() {
        JobBoardSources seeder = mock(JobBoardSources.class);
        when(freelances.findIdsWithoutJobBoardSources()).thenReturn(List.of(1L, 2L));
        when(seeder.seed(1L)).thenReturn(31);

        new JobBoardSourcesBackfill(freelances, seeder).seedExistingWorkspaces();

        verify(seeder).seed(1L);
        verify(seeder).seed(2L);
    }
}
