package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferDraftDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.JobOfferState;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.SourceType;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferImportServiceTest {

    private final OfferStateService offerStates = mock(OfferStateService.class);
    private final JobOfferStateRepository states = mock(JobOfferStateRepository.class);
    private final ClientRepository clients = mock(ClientRepository.class);
    private final SourceRepository sources = mock(SourceRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final ProjectNoteService projectNotes = mock(ProjectNoteService.class);
    private final OfferImportService service = new OfferImportService(offerStates, states, clients, sources, freelances, projects,
        projectService, projectNotes);

    private JobOffer offer;
    private JobOfferState state;

    @BeforeEach
    void setUp() {
        Freelance freelance = new Freelance();
        freelance.setId(3L);
        offer = new JobOffer();
        offer.setId(7L);
        offer.setSite(OfferSite.FREE_WORK);
        offer.setUrl("https://www.free-work.com/fr/tech-it/job-mission/java/dev-java");
        offer.setTitle("Développeur Java");
        offer.setCompany("Exemple Conseil");
        offer.setDescription("Refonte d'un SI");
        offer.setRateMin(550);
        offer.setRateMax(650);
        offer.setWorkMode(WorkMode.HYBRID);
        offer.setRemoteDaysPerWeek(2);
        offer.setStartDate(LocalDate.of(2026, 11, 2));
        offer.setDurationMonths(6);
        offer.setSkills(List.of("Java", "Spring"));
        offer.setCity("Paris");
        state = new JobOfferState();
        state.setFreelance(freelance);
        state.setOffer(offer);
        when(offerStates.groupStates(3L, 7L)).thenReturn(List.of(state));
        when(clients.save(any(Client.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 20L));
        when(sources.save(any(Source.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 30L));
        ProjectDto created = new ProjectDto();
        created.setId(40L);
        when(projectService.create(any(ProjectDto.class))).thenReturn(created);
        Project reference = new Project();
        reference.setId(40L);
        when(projects.getReferenceById(40L)).thenReturn(reference);
    }

    @Test
    void createsAContactOpportunityFromTheOfferWithTheSiteAsSource() {
        when(clients.findFirstByFreelanceIdAndCompanyNameIgnoreCase(3L, "Exemple Conseil")).thenReturn(Optional.empty());
        when(sources.findFirstByFreelanceIdAndNameIgnoreCase(3L, "Free-Work")).thenReturn(Optional.empty());

        ProjectDto created = service.importOffer(3L, 7L);

        assertThat(created.getId()).isEqualTo(40L);
        verify(projectNotes, never()).addNote(any(), any());
        ArgumentCaptor<ProjectDto> project = ArgumentCaptor.forClass(ProjectDto.class);
        verify(projectService).create(project.capture());
        ProjectDto sent = project.getValue();
        assertThat(sent.getRole()).isEqualTo("Développeur Java");
        assertThat(sent.getStatus()).isEqualTo(ProjectStatus.CONTACT);
        assertThat(sent.getDailyRate()).isEqualTo(650);
        assertThat(sent.getTechStack()).isEqualTo("Java, Spring");
        assertThat(sent.getWorkMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(sent.getRemoteDaysPerMonth()).isEqualTo(9);
        assertThat(sent.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(sent.getDurationInMonths()).isEqualTo(6);
        assertThat(sent.getLink()).isEqualTo(offer.getUrl());
        assertThat(sent.getFreelanceId()).isEqualTo(3L);
        assertThat(sent.getClientId()).isEqualTo(20L);
        assertThat(sent.getSourceId()).isEqualTo(30L);
        ArgumentCaptor<Client> client = ArgumentCaptor.forClass(Client.class);
        verify(clients).save(client.capture());
        assertThat(client.getValue().getCompanyName()).isEqualTo("Exemple Conseil");
        assertThat(client.getValue().getCity()).isEqualTo("Paris");
        assertThat(client.getValue().getIsFinal()).isFalse();
        ArgumentCaptor<Source> source = ArgumentCaptor.forClass(Source.class);
        verify(sources).save(source.capture());
        assertThat(source.getValue().getName()).isEqualTo("Free-Work");
        assertThat(source.getValue().getType()).isEqualTo(SourceType.JOB_BOARD);
        assertThat(source.getValue().getLink()).isEqualTo("https://www.free-work.com");
        assertThat(state.getImportedProject().getId()).isEqualTo(40L);
        assertThat(state.getSeenAt()).isNotNull();
        verify(states).save(state);
    }

    @Test
    void carriesTheWorkspacesNoteIntoTheOpportunity() {
        JobOfferState copy = new JobOfferState();
        copy.setNote("Rappeler Léa chez Exemple Conseil");
        when(offerStates.groupStates(3L, 7L)).thenReturn(List.of(state, copy));
        when(clients.findFirstByFreelanceIdAndCompanyNameIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(sources.findFirstByFreelanceIdAndNameIgnoreCase(any(), any())).thenReturn(Optional.empty());

        service.importOffer(3L, 7L);

        verify(projectNotes).addNote(40L, "Rappeler Léa chez Exemple Conseil");
    }

    @Test
    void reusesTheWorkspacesClientAndSourceAndFallsBackOnTheTargetRate() {
        offer.setCompany(null);
        offer.setRateMin(null);
        offer.setRateMax(null);
        offer.setSkills(List.of());
        offer.setWorkMode(WorkMode.ONSITE);
        Client site = withId(new Client(), 21L);
        Source existing = withId(new Source(), 31L);
        when(clients.findFirstByFreelanceIdAndCompanyNameIgnoreCase(3L, "Free-Work")).thenReturn(Optional.of(site));
        when(sources.findFirstByFreelanceIdAndNameIgnoreCase(3L, "Free-Work")).thenReturn(Optional.of(existing));
        when(freelances.findTargetDailyRate(3L)).thenReturn(Optional.of(700));

        service.importOffer(3L, 7L);

        ArgumentCaptor<ProjectDto> project = ArgumentCaptor.forClass(ProjectDto.class);
        verify(projectService).create(project.capture());
        assertThat(project.getValue().getClientId()).isEqualTo(21L);
        assertThat(project.getValue().getSourceId()).isEqualTo(31L);
        assertThat(project.getValue().getDailyRate()).isEqualTo(700);
        assertThat(project.getValue().getTechStack()).isNull();
        assertThat(project.getValue().getRemoteDaysPerMonth()).isNull();
        verify(clients, never()).save(any());
        verify(sources, never()).save(any());
    }

    @Test
    void usesTheMinimumRateThenZeroAndPlacesTheClientInTheRegion() {
        offer.setRateMax(null);
        offer.setCity(null);
        offer.setRegionCode("84");
        when(clients.findFirstByFreelanceIdAndCompanyNameIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(sources.findFirstByFreelanceIdAndNameIgnoreCase(any(), any())).thenReturn(Optional.empty());

        service.importOffer(3L, 7L);

        ArgumentCaptor<Client> client = ArgumentCaptor.forClass(Client.class);
        verify(clients).save(client.capture());
        assertThat(client.getValue().getCity()).isEqualTo("Auvergne-Rhône-Alpes");
        ArgumentCaptor<ProjectDto> project = ArgumentCaptor.forClass(ProjectDto.class);
        verify(projectService).create(project.capture());
        assertThat(project.getValue().getDailyRate()).isEqualTo(550);

        offer.setRateMin(null);
        offer.setRegionCode(null);
        state.setImportedProject(null);
        when(freelances.findTargetDailyRate(3L)).thenReturn(Optional.empty());
        service.importOffer(3L, 7L);
        verify(projectService, org.mockito.Mockito.times(2)).create(project.capture());
        assertThat(project.getValue().getDailyRate()).isZero();
    }

    @Test
    void draftsTheOpportunityWithoutCreatingAnything() {
        when(clients.findFirstByFreelanceIdAndCompanyNameIgnoreCase(3L, "Exemple Conseil")).thenReturn(Optional.empty());
        when(sources.findFirstByFreelanceIdAndNameIgnoreCase(3L, "Free-Work")).thenReturn(Optional.of(withId(new Source(), 31L)));

        OfferDraftDto draft = service.draft(3L, 7L);

        assertThat(draft.getClientName()).isEqualTo("Exemple Conseil");
        assertThat(draft.getSourceName()).isEqualTo("Free-Work");
        assertThat(draft.getProject().getClientId()).isNull();
        assertThat(draft.getProject().getSourceId()).isEqualTo(31L);
        assertThat(draft.getProject().getRole()).isEqualTo("Développeur Java");
        assertThat(draft.getProject().getDailyRate()).isEqualTo(650);
        assertThat(draft.getProject().getStatus()).isEqualTo(ProjectStatus.CONTACT);
        verify(clients, never()).save(any());
        verify(sources, never()).save(any());
        verify(projectService, never()).create(any());

        when(clients.findFirstByFreelanceIdAndCompanyNameIgnoreCase(3L, "Exemple Conseil")).thenReturn(Optional.of(withId(new Client(), 21L)));
        assertThat(service.draft(3L, 7L).getProject().getClientId()).isEqualTo(21L);
    }

    @Test
    void addsTheOpportunityAsCorrectedInTheForm() {
        when(clients.findFirstByFreelanceIdAndCompanyNameIgnoreCase(3L, "Exemple Conseil")).thenReturn(Optional.empty());
        when(sources.findFirstByFreelanceIdAndNameIgnoreCase(3L, "Free-Work")).thenReturn(Optional.empty());
        ProjectDto edits = new ProjectDto();
        edits.setId(99L);
        edits.setRole("Tech lead Java");
        edits.setDailyRate(700);
        edits.setStatus(ProjectStatus.INTERVIEW);
        edits.setMiddlemanId(22L);
        edits.setFreelanceId(5L);

        service.importOffer(3L, 7L, edits);

        ArgumentCaptor<ProjectDto> project = ArgumentCaptor.forClass(ProjectDto.class);
        verify(projectService).create(project.capture());
        ProjectDto sent = project.getValue();
        assertThat(sent.getId()).isNull();
        assertThat(sent.getFreelanceId()).isEqualTo(3L);
        assertThat(sent.getRole()).isEqualTo("Tech lead Java");
        assertThat(sent.getDailyRate()).isEqualTo(700);
        assertThat(sent.getStatus()).isEqualTo(ProjectStatus.INTERVIEW);
        assertThat(sent.getMiddlemanId()).isEqualTo(22L);
        // Unset in the form: the publisher, the site and the offer's link.
        assertThat(sent.getClientId()).isEqualTo(20L);
        assertThat(sent.getSourceId()).isEqualTo(30L);
        assertThat(sent.getLink()).isEqualTo(offer.getUrl());
        assertThat(state.getImportedProject().getId()).isEqualTo(40L);

        ProjectDto chosen = new ProjectDto();
        chosen.setRole("Développeur Java");
        chosen.setDailyRate(600);
        chosen.setClientId(23L);
        chosen.setSourceId(32L);
        chosen.setLink("https://example.com/mission");
        state.setImportedProject(null);
        service.importOffer(3L, 7L, chosen);
        verify(projectService, org.mockito.Mockito.times(2)).create(project.capture());
        assertThat(project.getValue()).extracting(ProjectDto::getClientId, ProjectDto::getSourceId, ProjectDto::getStatus, ProjectDto::getLink)
            .containsExactly(23L, 32L, ProjectStatus.CONTACT, "https://example.com/mission");
    }

    @Test
    void refusesAnOfferAlreadyInThePipeline() {
        Project project = new Project();
        project.setId(40L);
        state.setImportedProject(project);

        assertThatThrownBy(() -> service.importOffer(3L, 7L)).isInstanceOf(InvalidStateException.class).hasMessageContaining("40");
        verify(projectService, never()).create(any());
    }

    @Test
    void refusesAnOfferWhoseCopyIsAlreadyInThePipeline() {
        Project project = new Project();
        project.setId(41L);
        JobOfferState copy = new JobOfferState();
        copy.setImportedProject(project);
        when(offerStates.groupStates(3L, 7L)).thenReturn(List.of(state, copy));

        assertThatThrownBy(() -> service.importOffer(3L, 7L)).isInstanceOf(InvalidStateException.class).hasMessageContaining("41");
        verify(projectService, never()).create(any());
    }

    private static <T extends dev.swirlit.indezy.model.BaseEntity> T withId(T entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
