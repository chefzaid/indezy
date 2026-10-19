package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.dto.OfferStateExportDto;
import dev.swirlit.indezy.dto.OfferStateUpdateRequest;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.JobOfferState;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfferStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final JobOfferStateRepository states = mock(JobOfferStateRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final OfferStateService service = new OfferStateService(offers, states, freelances, Clock.fixed(NOW, ZoneOffset.UTC));
    private JobOffer offer;

    @BeforeEach
    void setUp() {
        offer = new JobOffer();
        offer.setId(7L);
        offer.setSite(OfferSite.LEHIBOU);
        offer.setTitle("Architecte");
        offer.setUrl("https://www.lehibou.com/annonce/7");
        when(states.save(any(JobOfferState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(states.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void appliesTheChangeToTheOffersCopies() {
        Freelance freelance = new Freelance();
        freelance.setId(3L);
        offer.setCopyGroup(7L);
        JobOffer copy = new JobOffer();
        copy.setId(8L);
        copy.setCopyGroup(7L);
        JobOfferState copyState = new JobOfferState();
        copyState.setOffer(copy);
        copyState.setSeenAt(NOW.minusSeconds(60));
        when(states.findByFreelanceIdAndOfferId(3L, 7L)).thenReturn(Optional.empty());
        when(states.findByFreelanceIdAndOfferId(3L, 8L)).thenReturn(Optional.of(copyState));
        when(offers.findById(7L)).thenReturn(Optional.of(offer));
        when(freelances.findById(3L)).thenReturn(Optional.of(freelance));
        when(offers.findCopyGroupOf(offer)).thenReturn(List.of(offer, copy));

        OfferStateDto state = service.update(3L, 7L, new OfferStateUpdateRequest(null, true, null));

        assertThat(copyState.isHidden()).isTrue();
        // The copy was seen: the mission was.
        assertThat(state).isEqualTo(new OfferStateDto(7L, false, true, true, null));
    }

    @Test
    void createsTheStateOnTheFirstAction() {
        Freelance freelance = new Freelance();
        freelance.setId(3L);
        when(states.findByFreelanceIdAndOfferId(3L, 7L)).thenReturn(Optional.empty());
        when(offers.findById(7L)).thenReturn(Optional.of(offer));
        when(freelances.findById(3L)).thenReturn(Optional.of(freelance));

        OfferStateDto state = service.update(3L, 7L, new OfferStateUpdateRequest(true, null, true));

        assertThat(state).isEqualTo(new OfferStateDto(7L, true, false, true, null));
    }

    @Test
    void changesOnlyWhatTheRequestNames() {
        JobOfferState existing = new JobOfferState();
        existing.setOffer(offer);
        existing.setSaved(true);
        existing.setSeenAt(NOW.minusSeconds(60));
        Project project = new Project();
        project.setId(12L);
        existing.setImportedProject(project);
        when(states.findByFreelanceIdAndOfferId(3L, 7L)).thenReturn(Optional.of(existing));

        OfferStateDto hidden = service.update(3L, 7L, new OfferStateUpdateRequest(null, true, true));

        assertThat(hidden).isEqualTo(new OfferStateDto(7L, true, true, true, 12L));
        assertThat(existing.getSeenAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(service.update(3L, 7L, new OfferStateUpdateRequest(false, false, false)))
            .isEqualTo(new OfferStateDto(7L, false, false, false, 12L));
    }

    @Test
    void refusesUnknownOffersAndWorkspaces() {
        when(states.findByFreelanceIdAndOfferId(any(), any())).thenReturn(Optional.empty());
        when(offers.findById(8L)).thenReturn(Optional.empty());
        when(offers.findById(7L)).thenReturn(Optional.of(offer));
        when(freelances.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(3L, 8L, new OfferStateUpdateRequest(true, null, null)))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update(4L, 7L, new OfferStateUpdateRequest(true, null, null)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void exportsWhatTheWorkspaceDid() {
        JobOfferState state = new JobOfferState();
        state.setOffer(offer);
        state.setHidden(true);
        state.setSeenAt(NOW);
        when(states.findByFreelanceId(3L)).thenReturn(List.of(state));

        assertThat(service.export(3L)).containsExactly(new OfferStateExportDto(OfferSite.LEHIBOU, "Architecte", null,
            "https://www.lehibou.com/annonce/7", false, true, NOW, null));
    }
}
