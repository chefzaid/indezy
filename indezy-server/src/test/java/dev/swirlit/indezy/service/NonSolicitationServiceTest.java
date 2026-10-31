package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NonSolicitationDto;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NonSolicitationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);

    private final ProjectRepository repository = mock(ProjectRepository.class);
    private final NonSolicitationService service = new NonSolicitationService(repository);
    private final List<Project> projects = new ArrayList<>();
    private final Client bank = client(1, "Banque");
    private final Client esn = client(2, "ESN Conseil");
    private final Client otherEsn = client(3, "Autre ESN");

    private static Client client(long id, String name) {
        Client client = new Client();
        client.setId(id);
        client.setCompanyName(name);
        return client;
    }

    private Project project(long id, ProjectStatus status, Client client, Client middleman) {
        Project project = new Project();
        project.setId(id);
        project.setRole("Role " + id);
        project.setStatus(status);
        project.setClient(client);
        project.setMiddleman(middleman);
        projects.add(project);
        return project;
    }

    private List<NonSolicitationDto> covered() {
        when(repository.findByFreelanceId(1L)).thenReturn(projects);
        return service.forFreelance(1L, TODAY);
    }

    @Test
    void flagsTheEndClientDirectlyOrThroughAnotherEsnUntilTheClauseEnds() {
        Project mission = project(1, ProjectStatus.WON, bank, esn);
        mission.setNonSolicitationMonths(12);
        mission.setEndedOn(LocalDate.of(2026, 3, 31));
        project(2, ProjectStatus.CONTACT, bank, null);
        project(3, ProjectStatus.INTERVIEW, bank, otherEsn);
        project(4, ProjectStatus.CONTACT, bank, esn);
        project(5, ProjectStatus.CONTACT, client(9, "Ailleurs"), null);
        project(6, ProjectStatus.CONTACT, bank, null).setArchivedAt(LocalDateTime.of(2026, 10, 1, 9, 0));

        assertThat(covered()).containsExactly(
            new NonSolicitationDto(2L, 1L, "Role 1", "ESN Conseil", "Banque", 12, LocalDate.of(2027, 3, 31)),
            new NonSolicitationDto(3L, 1L, "Role 1", "ESN Conseil", "Banque", 12, LocalDate.of(2027, 3, 31)));
    }

    @Test
    void endsOnceTheMonthsHavePassedAndHasNoDateWhileTheMissionRunsOpenEnded() {
        Project ended = project(1, ProjectStatus.WON, bank, esn);
        ended.setNonSolicitationMonths(6);
        ended.setEndedOn(LocalDate.of(2026, 4, 30));
        project(2, ProjectStatus.CONTACT, bank, null);
        assertThat(covered()).isEmpty();

        ended.setEndedOn(null);
        assertThat(covered()).singleElement().extracting(NonSolicitationDto::endsOn).isNull();
        ended.setStartDate(LocalDate.of(2026, 9, 1));
        ended.setDurationInMonths(6);
        assertThat(covered()).singleElement().extracting(NonSolicitationDto::endsOn).isEqualTo(LocalDate.of(2027, 8, 28));
    }

    @Test
    void needsAWonMissionThroughAnEsnWithItsMonthsRecorded() {
        project(1, ProjectStatus.WON, bank, esn);
        Project direct = project(2, ProjectStatus.WON, bank, null);
        direct.setNonSolicitationMonths(12);
        Project lost = project(3, ProjectStatus.LOST, bank, esn);
        lost.setNonSolicitationMonths(12);
        Project noClient = project(4, ProjectStatus.WON, null, esn);
        noClient.setNonSolicitationMonths(12);
        project(5, ProjectStatus.CONTACT, bank, otherEsn);
        assertThat(covered()).isEmpty();
    }
}
