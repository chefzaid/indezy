package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.KanbanBoardDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.StepStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The Kanban board, favourites, manual order and tags of the project service. */
class ProjectBoardServiceTest extends ProjectServiceTestSupport {

    @Test
    void getKanbanBoard_WithSeason_ShouldOnlyLoadThatSeason() {
        testProject.setStatus(ProjectStatus.OFFER);
        when(projectRepository.findByFreelanceIdAndSeasonId(1L, 3L)).thenReturn(List.of(testProject));
        when(interviewStepRepository.findByProjectId(any())).thenReturn(List.of());

        KanbanBoardDto board = projectService.getKanbanBoard(1L, 3L);

        assertThat(board.getColumns().get("OFFER")).hasSize(1);
        verify(projectRepository, never()).findByFreelanceId(any());
    }

    @Test
    void getKanbanBoard_ShouldGroupProjectsByStatusInColumnOrder() {
        // Given
        testProject.setStatus(ProjectStatus.INTERVIEW);

        Project projectWithoutStatus = new Project();
        projectWithoutStatus.setId(2L);
        projectWithoutStatus.setRole("Backend Developer");
        projectWithoutStatus.setFreelance(testFreelance);

        InterviewStep completedStep = new InterviewStep();
        completedStep.setStatus(StepStatus.VALIDATED);
        InterviewStep failedStep = new InterviewStep();
        failedStep.setStatus(StepStatus.FAILED);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(Arrays.asList(testProject, projectWithoutStatus));
        when(interviewStepRepository.findByProjectId(1L)).thenReturn(Arrays.asList(completedStep, failedStep));
        when(interviewStepRepository.findByProjectId(2L)).thenReturn(List.of());

        // When
        KanbanBoardDto board = projectService.getKanbanBoard(1L, null);

        // Then
        assertThat(board.getColumnOrder()).containsExactly(
                "CONTACT", "INTERVIEW", "OFFER", "WON", "LOST");
        assertThat(board.getColumns().get("INTERVIEW")).hasSize(1);
        assertThat(board.getColumns().get("CONTACT")).hasSize(1); // null status defaults to CONTACT

        KanbanBoardDto.ProjectCardDto card = board.getColumns().get("INTERVIEW").get(0);
        assertThat(card.getProjectId()).isEqualTo(1L);
        assertThat(card.getClientName()).isEqualTo("Test Company");
        assertThat(card.getTotalSteps()).isEqualTo(2);
        assertThat(card.getCompletedSteps()).isEqualTo(1);
        assertThat(card.getFailedSteps()).isEqualTo(1);
    }

    @Test
    void getKanbanBoard_ShouldPinFavoritesToTopOfColumn() {
        // Given two projects in the same column, only the second is a favorite.
        testProject.setStatus(ProjectStatus.CONTACT);
        testProject.setIsFavorite(false);

        Project favorite = new Project();
        favorite.setId(2L);
        favorite.setRole("Lead Developer");
        favorite.setStatus(ProjectStatus.CONTACT);
        favorite.setFreelance(testFreelance);
        favorite.setIsFavorite(true);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(Arrays.asList(testProject, favorite));
        when(interviewStepRepository.findByProjectId(any())).thenReturn(List.of());

        // When
        KanbanBoardDto board = projectService.getKanbanBoard(1L, null);

        // Then the favorite is listed first.
        List<KanbanBoardDto.ProjectCardDto> applied = board.getColumns().get("CONTACT");
        assertThat(applied).hasSize(2);
        assertThat(applied.get(0).getProjectId()).isEqualTo(2L);
        assertThat(applied.get(0).getIsFavorite()).isTrue();
        assertThat(applied.get(1).getProjectId()).isEqualTo(1L);
    }

    @Test
    void getKanbanBoard_ShouldOrderByBoardPositionWithinColumn() {
        // Given three non-favorite cards with positions 2, 0 and none.
        testProject.setStatus(ProjectStatus.CONTACT);
        testProject.setBoardPosition(2);

        Project first = new Project();
        first.setId(2L);
        first.setStatus(ProjectStatus.CONTACT);
        first.setFreelance(testFreelance);
        first.setBoardPosition(0);

        Project noPosition = new Project();
        noPosition.setId(3L);
        noPosition.setStatus(ProjectStatus.CONTACT);
        noPosition.setFreelance(testFreelance);

        when(projectRepository.findByFreelanceId(1L))
                .thenReturn(Arrays.asList(testProject, first, noPosition));
        when(interviewStepRepository.findByProjectId(any())).thenReturn(List.of());

        // When
        KanbanBoardDto board = projectService.getKanbanBoard(1L, null);

        // Then lower positions come first and the position-less card sorts last.
        assertThat(board.getColumns().get("CONTACT"))
                .extracting(KanbanBoardDto.ProjectCardDto::getProjectId)
                .containsExactly(2L, 1L, 3L);
    }

    @Test
    void reorderKanbanColumn_ShouldAssignPositionsByIndexAndIgnoreForeignProjects() {
        // Given two cards owned by the freelance and one owned by someone else.
        Project ownedA = new Project();
        ownedA.setId(10L);
        ownedA.setFreelance(testFreelance);
        Project ownedB = new Project();
        ownedB.setId(11L);
        ownedB.setFreelance(testFreelance);
        Freelance otherFreelance = new Freelance();
        otherFreelance.setId(2L);
        Project foreign = new Project();
        foreign.setId(12L);
        foreign.setFreelance(otherFreelance);

        List<Long> orderedIds = List.of(11L, 10L, 12L);
        when(projectRepository.findAllById(orderedIds)).thenReturn(List.of(ownedA, ownedB, foreign));

        // When
        projectService.reorderKanbanColumn(1L, orderedIds);

        // Then owned cards get their index as position; the foreign card is left untouched.
        assertThat(ownedB.getBoardPosition()).isZero();
        assertThat(ownedA.getBoardPosition()).isEqualTo(1);
        assertThat(foreign.getBoardPosition()).isNull();
        verify(projectRepository).saveAll(any());
    }

    @Test
    void getKanbanBoard_ShouldFlagProjectsWithSameClientAndRoleAsDuplicates() {
        // Given two opportunities for the same client with the same role (different case),
        // plus a third with a different role for the same client.
        testProject.setStatus(ProjectStatus.CONTACT);

        Project duplicate = new Project();
        duplicate.setId(2L);
        duplicate.setRole("full stack developer");
        duplicate.setStatus(ProjectStatus.CONTACT);
        duplicate.setFreelance(testFreelance);
        duplicate.setClient(testClient);

        Project distinct = new Project();
        distinct.setId(3L);
        distinct.setRole("DevOps Engineer");
        distinct.setStatus(ProjectStatus.CONTACT);
        distinct.setFreelance(testFreelance);
        distinct.setClient(testClient);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(Arrays.asList(testProject, duplicate, distinct));
        when(interviewStepRepository.findByProjectId(any())).thenReturn(List.of());

        // When
        KanbanBoardDto board = projectService.getKanbanBoard(1L, null);

        // Then the matching pair is flagged, the unique role is not.
        Map<Long, Boolean> flagsById = board.getColumns().values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toMap(
                KanbanBoardDto.ProjectCardDto::getProjectId,
                KanbanBoardDto.ProjectCardDto::getIsPotentialDuplicate));
        assertThat(flagsById.get(1L)).isTrue();
        assertThat(flagsById.get(2L)).isTrue();
        assertThat(flagsById.get(3L)).isFalse();
    }

    @Test
    void getKanbanBoard_WithUniqueRoles_ShouldFlagNoDuplicates() {
        // Given a single project, nothing can be a duplicate.
        testProject.setStatus(ProjectStatus.CONTACT);
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject));
        when(interviewStepRepository.findByProjectId(any())).thenReturn(List.of());

        // When
        KanbanBoardDto board = projectService.getKanbanBoard(1L, null);

        // Then
        KanbanBoardDto.ProjectCardDto card = board.getColumns().get("CONTACT").get(0);
        assertThat(card.getIsPotentialDuplicate()).isFalse();
    }

    @Test
    void toggleFavorite_ShouldFlipFlagAndPersist() {
        // Given
        testProject.setIsFavorite(false);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(projectRepository.save(testProject)).thenReturn(testProject);
        when(projectMapper.toDto(testProject)).thenReturn(testProjectDto);

        // When
        projectService.toggleFavorite(1L);

        // Then
        assertThat(testProject.getIsFavorite()).isTrue();
        verify(projectRepository).save(testProject);
    }

    @Test
    void toggleFavorite_WhenProjectNotExists_ShouldThrowResourceNotFoundException() {
        // Given
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> projectService.toggleFavorite(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void renameTag_ShouldReplaceTagCaseInsensitivelyAndCollapseDuplicates() {
        Project p1 = new Project();
        p1.setTechStack("Angular 18, Spring Boot, PostgreSQL");
        Project p2 = new Project();
        p2.setTechStack("angular 18, Angular, Docker");
        Project p3 = new Project();
        p3.setTechStack("Java, Kubernetes");
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(p1, p2, p3));

        int updated = projectService.renameTag(1L, "Angular 18", "Angular");

        // p1 and p2 change; p3 has no matching tag.
        assertThat(updated).isEqualTo(2);
        assertThat(p1.getTechStack()).isEqualTo("Angular, Spring Boot, PostgreSQL");
        // "angular 18" -> "Angular", which already exists -> collapsed.
        assertThat(p2.getTechStack()).isEqualTo("Angular, Docker");
        assertThat(p3.getTechStack()).isEqualTo("Java, Kubernetes");
        verify(projectRepository, times(2)).save(any(Project.class));
    }

    @Test
    void renameTag_ShouldRejectBlankTagNames() {
        assertThatThrownBy(() -> projectService.renameTag(1L, "  ", "Angular"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(projectRepository, never()).findByFreelanceId(anyLong());
    }
}
