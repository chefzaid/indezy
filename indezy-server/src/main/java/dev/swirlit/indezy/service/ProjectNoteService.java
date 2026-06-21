package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.ProjectNoteDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.ProjectNote;
import dev.swirlit.indezy.repository.ProjectNoteRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages the chronological journal of notes attached to a project: every call, email or decision
 * can be logged and shown newest-first as a timeline.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectNoteService {

    private final ProjectNoteRepository projectNoteRepository;
    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<ProjectNoteDto> getNotesForProject(Long projectId) {
        log.debug("Getting notes for project: {}", projectId);
        return projectNoteRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .map(this::toDto)
            .toList();
    }

    public ProjectNoteDto addNote(Long projectId, String content) {
        log.debug("Adding note to project: {}", projectId);
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format(ErrorMessages.PROJECT_NOT_FOUND, projectId)));

        ProjectNote note = new ProjectNote();
        note.setContent(content);
        note.setProject(project);
        ProjectNote saved = projectNoteRepository.save(note);
        log.info("Added note with id {} to project {}", saved.getId(), projectId);
        return toDto(saved);
    }

    public void deleteNote(Long projectId, Long noteId) {
        log.debug("Deleting note {} from project {}", noteId, projectId);
        ProjectNote note = projectNoteRepository.findById(noteId)
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format(ErrorMessages.PROJECT_NOTE_NOT_FOUND, noteId)));
        if (note.getProject() == null || !note.getProject().getId().equals(projectId)) {
            throw new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOTE_NOT_FOUND, noteId));
        }
        projectNoteRepository.delete(note);
        log.info("Deleted note {} from project {}", noteId, projectId);
    }

    private ProjectNoteDto toDto(ProjectNote note) {
        return ProjectNoteDto.builder()
            .id(note.getId())
            .projectId(note.getProject() != null ? note.getProject().getId() : null)
            .content(note.getContent())
            .createdAt(note.getCreatedAt())
            .build();
    }
}
