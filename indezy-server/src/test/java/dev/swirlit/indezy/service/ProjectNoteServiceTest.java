package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ProjectNoteDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.ProjectNote;
import dev.swirlit.indezy.repository.ProjectNoteRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectNoteServiceTest {

    @Mock
    private ProjectNoteRepository projectNoteRepository;
    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectNoteService projectNoteService;

    private Project project;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setId(1L);
        project.setRole("Backend Dev");
    }

    @Test
    void getNotesForProject_ShouldReturnNotesNewestFirst() {
        ProjectNote note = new ProjectNote();
        note.setId(10L);
        note.setContent("Called the client");
        note.setProject(project);
        when(projectNoteRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(note));

        List<ProjectNoteDto> notes = projectNoteService.getNotesForProject(1L);

        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).getId()).isEqualTo(10L);
        assertThat(notes.get(0).getProjectId()).isEqualTo(1L);
        assertThat(notes.get(0).getContent()).isEqualTo("Called the client");
    }

    @Test
    void addNote_ShouldPersistNoteForExistingProject() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectNoteRepository.save(any(ProjectNote.class))).thenAnswer(invocation -> {
            ProjectNote saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        ProjectNoteDto result = projectNoteService.addNote(1L, "Negotiated the rate");

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getProjectId()).isEqualTo(1L);
        assertThat(result.getContent()).isEqualTo("Negotiated the rate");
    }

    @Test
    void addNote_WithUnknownProject_ShouldThrow() {
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectNoteService.addNote(404L, "Note"))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(projectNoteRepository, never()).save(any());
    }

    @Test
    void deleteNote_ShouldRemoveNoteBelongingToProject() {
        ProjectNote note = new ProjectNote();
        note.setId(10L);
        note.setProject(project);
        when(projectNoteRepository.findById(10L)).thenReturn(Optional.of(note));

        projectNoteService.deleteNote(1L, 10L);

        verify(projectNoteRepository).delete(note);
    }

    @Test
    void deleteNote_WhenNoteBelongsToAnotherProject_ShouldThrowAndNotDelete() {
        Project otherProject = new Project();
        otherProject.setId(2L);
        ProjectNote note = new ProjectNote();
        note.setId(10L);
        note.setProject(otherProject);
        when(projectNoteRepository.findById(10L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> projectNoteService.deleteNote(1L, 10L))
            .isInstanceOf(ResourceNotFoundException.class);
        verify(projectNoteRepository, never()).delete(any());
    }
}
