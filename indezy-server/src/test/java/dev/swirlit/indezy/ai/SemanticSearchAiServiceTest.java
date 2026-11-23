package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.ProjectNote;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.ProjectNoteRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticSearchAiServiceTest {

    private final AiService ai = mock(AiService.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectNoteRepository notes = mock(ProjectNoteRepository.class);
    private final SemanticSearchAiService service = new SemanticSearchAiService(ai, projects, notes);

    private static Project project(long id, String role, String description) {
        Project project = new Project();
        project.setId(id);
        project.setRole(role);
        project.setDescription(description);
        project.setStatus(ProjectStatus.LOST);
        project.setCreatedAt(LocalDateTime.of(2026, 1, (int) id, 9, 0));
        Client client = new Client();
        client.setCompanyName("Banque Exemple");
        project.setClient(client);
        return project;
    }

    @Test
    void givesTheAiTheRecordsAndKeepsOnlyTheIdsItWasGivenOnceEach() {
        Project payments = project(2, "Lead Java", "Refonte du SI de paiement, Kafka et Spring");
        Project data = project(3, "Data engineer", "Pipelines Spark");
        ProjectNote note = new ProjectNote();
        note.setId(7L);
        note.setProject(payments);
        note.setContent("Le client a trouvé notre TJM trop élevé, " + "très ".repeat(80) + "fin");
        when(projects.findByFreelanceId(5L)).thenReturn(List.of(data, payments));
        when(notes.findByFreelanceId(5L)).thenReturn(List.of(note));
        when(ai.structured(anyLong(), eq(AiFeature.SEARCH), any(), any(), eq(SemanticSearchAiService.Reading.class), anyLong(),
            eq(OutputConfig.Effort.LOW))).thenReturn(new SemanticSearchAiService.Reading(List.of(
                new SemanticSearchAiService.Found("n7", " Le TJM y a été jugé trop haut. "), new SemanticSearchAiService.Found("O99", "invented"),
                new SemanticSearchAiService.Found("N7", "again"), new SemanticSearchAiService.Found("O2", "Paiements"))));

        List<SemanticSearchAiService.Hit> hits = service.search(1L, 5L, "missions perdues à cause du tarif");

        assertThat(hits).extracting(SemanticSearchAiService.Hit::kind, SemanticSearchAiService.Hit::projectId, SemanticSearchAiService.Hit::why)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("NOTE", 2L, "Le TJM y a été jugé trop haut."),
                org.assertj.core.groups.Tuple.tuple("OPPORTUNITY", 2L, "Paiements"));
        assertThat(hits.getFirst().title()).isEqualTo("Lead Java · Banque Exemple");
        assertThat(hits.getFirst().excerpt()).hasSize(240).endsWith("…");
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(ai).structured(eq(1L), eq(AiFeature.SEARCH), any(), text.capture(), eq(SemanticSearchAiService.Reading.class), anyLong(), any());
        // The newest opportunity first, then the notes.
        assertThat(text.getValue()).contains("<question>\nmissions perdues à cause du tarif\n</question>");
        assertThat(text.getValue().indexOf("O3 | Data engineer")).isLessThan(text.getValue().indexOf("O2 | Lead Java")).isPositive();
        assertThat(text.getValue()).contains("N7 | on Lead Java · Banque Exemple | Le client a trouvé");
    }

    @Test
    void asksNothingOfAWorkspaceWithoutRecords() {
        assertThat(service.search(1L, 5L, "tarif")).isEmpty();
        verify(ai, never()).structured(anyLong(), any(), any(), any(), any(), anyLong(), any());
    }
}
