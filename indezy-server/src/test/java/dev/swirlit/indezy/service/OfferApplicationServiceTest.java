package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.InterviewStepDto;
import dev.swirlit.indezy.dto.OfferApplicationRequest;
import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.JobOfferState;
import dev.swirlit.indezy.model.OfferApplication;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ApplicationChannel;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.StepStatus;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferApplicationServiceTest {

    /** 16:30 in Paris. */
    private static final Instant NOW = Instant.parse("2026-10-20T14:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    private final OfferStateService offerStates = mock(OfferStateService.class);
    private final OfferImportService importService = mock(OfferImportService.class);
    private final JobOfferStateRepository states = mock(JobOfferStateRepository.class);
    private final InterviewStepService steps = mock(InterviewStepService.class);
    private final OfferApplicationService service = new OfferApplicationService(offerStates, importService, states, steps,
        Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(states.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void addsAMissionToThePipelineWithItsApplicationAndFollowUp() {
        JobOfferState offer = state(7L, OfferSite.FREE_WORK, null);
        JobOfferState copy = state(8L, OfferSite.COLLECTIVE, null);
        Project project = project(40L);
        JobOfferState imported = state(7L, OfferSite.FREE_WORK, project);
        imported.setSeenAt(NOW.minusSeconds(3600));
        when(offerStates.groupStates(3L, 7L)).thenReturn(List.of(offer, copy), List.of(imported, copy));
        ProjectDto created = new ProjectDto();
        created.setId(40L);
        when(importService.importOffer(3L, 7L)).thenReturn(created);

        OfferStateDto state = service.apply(3L, 7L, new OfferApplicationRequest(TODAY, ApplicationChannel.EMAIL, 7, DocumentLanguage.FR));

        OfferApplication application = new OfferApplication(TODAY, ApplicationChannel.EMAIL);
        assertThat(state.application()).isEqualTo(application);
        assertThat(state.importedProjectId()).isEqualTo(40L);
        assertThat(state.seen()).isTrue();
        assertThat(copy.getApplication()).isEqualTo(application);
        assertThat(copy.getSeenAt()).isEqualTo(NOW);
        assertThat(imported.getSeenAt()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(offer.getApplication()).isNull();
        List<InterviewStepDto> added = steps();
        assertThat(added).extracting(InterviewStepDto::getTitle).containsExactly("Candidature envoyée (e-mail)", "Relancer la candidature");
        assertThat(added).extracting(InterviewStepDto::getStatus).containsExactly(StepStatus.WAITING_FEEDBACK, StepStatus.PLANNED);
        assertThat(added).extracting(InterviewStepDto::getDate)
            .containsExactly(LocalDateTime.of(2026, 10, 20, 16, 30), LocalDateTime.of(2026, 10, 27, 9, 0));
        assertThat(added).extracting(InterviewStepDto::getProjectId).containsOnly(40L);
        assertThat(added.get(0).getNotes()).isEqualTo("Free-Work · https://example.test/7");
    }

    @Test
    void keepsTheOpportunityAlreadyInThePipelineAndPlansALateFollowUpForTomorrow() {
        JobOfferState offer = state(7L, OfferSite.FREE_WORK, project(12L));
        when(offerStates.groupStates(3L, 7L)).thenReturn(List.of(offer));

        service.apply(3L, 7L, new OfferApplicationRequest(TODAY.minusDays(5), ApplicationChannel.PHONE, 3, null));

        verify(importService, never()).importOffer(any(), any());
        List<InterviewStepDto> added = steps();
        assertThat(added).extracting(InterviewStepDto::getTitle).containsExactly("Applied by phone", "Follow up on the application");
        assertThat(added).extracting(InterviewStepDto::getDate)
            .containsExactly(LocalDateTime.of(2026, 10, 15, 9, 0), LocalDateTime.of(2026, 10, 21, 9, 0));
        assertThat(added).extracting(InterviewStepDto::getProjectId).containsOnly(12L);
    }

    @Test
    void plansNoFollowUpUnlessAsked() {
        when(offerStates.groupStates(3L, 7L)).thenReturn(List.of(state(7L, OfferSite.FREE_WORK, project(12L))));

        service.apply(3L, 7L, new OfferApplicationRequest(TODAY, ApplicationChannel.SITE_FORM, null, DocumentLanguage.EN));

        assertThat(steps()).extracting(InterviewStepDto::getTitle).containsExactly("Application sent (site form)");
    }

    @Test
    void refusesALaterDayOrASecondApplication() {
        JobOfferState applied = state(7L, OfferSite.FREE_WORK, project(12L));
        applied.setApplication(new OfferApplication(TODAY.minusDays(2), ApplicationChannel.EMAIL));
        when(offerStates.groupStates(3L, 7L)).thenReturn(List.of(applied));

        assertThatThrownBy(() -> service.apply(3L, 7L, new OfferApplicationRequest(TODAY.plusDays(1), ApplicationChannel.EMAIL, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.apply(3L, 7L, new OfferApplicationRequest(TODAY, ApplicationChannel.EMAIL, null, null)))
            .isInstanceOf(InvalidStateException.class)
            .hasMessageContaining("2026-10-18");
        verify(steps, never()).create(any());
    }

    @Test
    void readsTodayInParis() {
        OfferApplicationService lateEvening = new OfferApplicationService(offerStates, importService, states, steps,
            Clock.fixed(Instant.parse("2026-10-20T22:30:00Z"), ZoneOffset.UTC));
        when(offerStates.groupStates(3L, 7L)).thenReturn(List.of(state(7L, OfferSite.FREE_WORK, project(12L))));

        lateEvening.apply(3L, 7L, new OfferApplicationRequest(TODAY.plusDays(1), ApplicationChannel.EMAIL, null, null));

        assertThat(steps()).extracting(InterviewStepDto::getDate).containsExactly(LocalDateTime.of(2026, 10, 21, 0, 30));
    }

    @Test
    void forgetsAnApplicationOnEveryCopy() {
        JobOfferState offer = state(7L, OfferSite.FREE_WORK, project(12L));
        JobOfferState copy = state(8L, OfferSite.COLLECTIVE, null);
        offer.setApplication(new OfferApplication(TODAY, ApplicationChannel.EMAIL));
        copy.setApplication(new OfferApplication(TODAY, ApplicationChannel.EMAIL));
        when(offerStates.groupStates(3L, 7L)).thenReturn(List.of(offer, copy));

        OfferStateDto state = service.withdraw(3L, 7L);

        assertThat(state.application()).isNull();
        assertThat(state.importedProjectId()).isEqualTo(12L);
        assertThat(copy.getApplication()).isNull();
        verify(steps, never()).create(any());
    }

    private List<InterviewStepDto> steps() {
        ArgumentCaptor<InterviewStepDto> added = ArgumentCaptor.forClass(InterviewStepDto.class);
        verify(steps, atLeastOnce()).create(added.capture());
        return added.getAllValues();
    }

    private static JobOfferState state(Long offerId, OfferSite site, Project project) {
        JobOffer offer = new JobOffer();
        offer.setId(offerId);
        offer.setSite(site);
        offer.setUrl("https://example.test/" + offerId);
        JobOfferState state = new JobOfferState();
        state.setOffer(offer);
        state.setImportedProject(project);
        return state;
    }

    private static Project project(Long id) {
        Project project = new Project();
        project.setId(id);
        return project;
    }
}
