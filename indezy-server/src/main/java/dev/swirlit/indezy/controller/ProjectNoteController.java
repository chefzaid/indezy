package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ProjectNoteDto;
import dev.swirlit.indezy.service.ProjectNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects/{projectId}/notes")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
@Tag(name = "Project Notes", description = "Chronological journal of notes per project")
public class ProjectNoteController {

    private final ProjectNoteService projectNoteService;

    @Operation(summary = "List project notes", description = "Get a project's notes, newest first")
    @GetMapping
    public ResponseEntity<List<ProjectNoteDto>> getNotes(@PathVariable Long projectId) {
        log.debug("GET /projects/{}/notes - Listing notes", projectId);
        return ResponseEntity.ok(projectNoteService.getNotesForProject(projectId));
    }

    @Operation(summary = "Add project note", description = "Append a note to the project's journal")
    @PostMapping
    public ResponseEntity<ProjectNoteDto> addNote(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectNoteDto request) {
        log.debug("POST /projects/{}/notes - Adding note", projectId);
        ProjectNoteDto created = projectNoteService.addNote(projectId, request.getContent());
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @Operation(summary = "Delete project note", description = "Remove a note from the project's journal")
    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable Long projectId, @PathVariable Long noteId) {
        log.debug("DELETE /projects/{}/notes/{} - Deleting note", projectId, noteId);
        projectNoteService.deleteNote(projectId, noteId);
        return ResponseEntity.noContent().build();
    }
}
