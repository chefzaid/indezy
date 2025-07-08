package dev.byteworks.indezy.controller;

import dev.byteworks.indezy.dto.InterviewStepDto;
import dev.byteworks.indezy.dto.KanbanBoardDto;
import dev.byteworks.indezy.dto.StepTransitionDto;
import dev.byteworks.indezy.model.enums.StepStatus;
import dev.byteworks.indezy.service.InterviewStepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/interview-steps")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
@Tag(name = "Interview Steps", description = "Interview step management operations")
public class InterviewStepController {

    private final InterviewStepService interviewStepService;

    @Operation(summary = "Get all interview steps", description = "Retrieve a list of all interview steps")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved interview steps",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = InterviewStepDto.class)))
    })
    @GetMapping
    public ResponseEntity<List<InterviewStepDto>> getAllInterviewSteps() {
        log.debug("GET /interview-steps - Getting all interview steps");
        List<InterviewStepDto> interviewSteps = interviewStepService.findAll();
        return ResponseEntity.ok(interviewSteps);
    }

    @Operation(summary = "Get interview step by ID", description = "Retrieve a specific interview step by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved interview step",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = InterviewStepDto.class))),
        @ApiResponse(responseCode = "404", description = "Interview step not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<InterviewStepDto> getInterviewStepById(
            @Parameter(description = "Interview step ID", required = true) @PathVariable Long id) {
        log.debug("GET /interview-steps/{} - Getting interview step by id", id);
        InterviewStepDto interviewStep = interviewStepService.findById(id);
        return ResponseEntity.ok(interviewStep);
    }

    @Operation(summary = "Get interview steps by project ID", description = "Retrieve all interview steps for a specific project")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved interview steps",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = InterviewStepDto.class)))
    })
    @GetMapping("/by-project/{projectId}")
    public ResponseEntity<List<InterviewStepDto>> getInterviewStepsByProjectId(
            @Parameter(description = "Project ID", required = true) @PathVariable Long projectId) {
        log.debug("GET /interview-steps/by-project/{} - Getting interview steps by project id", projectId);
        List<InterviewStepDto> interviewSteps = interviewStepService.findByProjectId(projectId);
        return ResponseEntity.ok(interviewSteps);
    }

    @Operation(summary = "Get interview steps by project ID ordered by date", description = "Retrieve all interview steps for a specific project ordered by date")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved interview steps",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = InterviewStepDto.class)))
    })
    @GetMapping("/by-project/{projectId}/ordered")
    public ResponseEntity<List<InterviewStepDto>> getInterviewStepsByProjectIdOrderByDate(
            @Parameter(description = "Project ID", required = true) @PathVariable Long projectId) {
        log.debug("GET /interview-steps/by-project/{}/ordered - Getting interview steps by project id ordered by date", projectId);
        List<InterviewStepDto> interviewSteps = interviewStepService.findByProjectIdOrderByDate(projectId);
        return ResponseEntity.ok(interviewSteps);
    }

    @Operation(summary = "Get interview steps by freelance ID and status", description = "Retrieve interview steps for a freelance with specific status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved interview steps",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = InterviewStepDto.class)))
    })
    @GetMapping("/by-freelance/{freelanceId}")
    public ResponseEntity<List<InterviewStepDto>> getInterviewStepsByFreelanceIdAndStatus(
            @Parameter(description = "Freelance ID", required = true) @PathVariable Long freelanceId,
            @Parameter(description = "Step status") @RequestParam(required = false) StepStatus status) {
        log.debug("GET /interview-steps/by-freelance/{} - Getting interview steps by freelance id and status: {}", freelanceId, status);
        List<InterviewStepDto> interviewSteps = interviewStepService.findByFreelanceIdAndStatus(freelanceId, status);
        return ResponseEntity.ok(interviewSteps);
    }

    @Operation(summary = "Create new interview step", description = "Create a new interview step with the provided details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Interview step created successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = InterviewStepDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid interview step data")
    })
    @PostMapping
    public ResponseEntity<InterviewStepDto> createInterviewStep(
            @Parameter(description = "Interview step details", required = true) @Valid @RequestBody InterviewStepDto interviewStepDto) {
        log.debug("POST /interview-steps - Creating new interview step");
        InterviewStepDto createdInterviewStep = interviewStepService.create(interviewStepDto);
        return new ResponseEntity<>(createdInterviewStep, HttpStatus.CREATED);
    }

    @Operation(summary = "Update interview step", description = "Update an existing interview step")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Interview step updated successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = InterviewStepDto.class))),
        @ApiResponse(responseCode = "404", description = "Interview step not found"),
        @ApiResponse(responseCode = "400", description = "Invalid interview step data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<InterviewStepDto> updateInterviewStep(
            @Parameter(description = "Interview step ID", required = true) @PathVariable Long id,
            @Parameter(description = "Updated interview step details", required = true) @Valid @RequestBody InterviewStepDto interviewStepDto) {
        log.debug("PUT /interview-steps/{} - Updating interview step", id);
        InterviewStepDto updatedInterviewStep = interviewStepService.update(id, interviewStepDto);
        return ResponseEntity.ok(updatedInterviewStep);
    }

    @Operation(summary = "Delete interview step", description = "Delete an interview step by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Interview step deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Interview step not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInterviewStep(
            @Parameter(description = "Interview step ID", required = true) @PathVariable Long id) {
        log.debug("DELETE /interview-steps/{} - Deleting interview step", id);
        interviewStepService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update interview step status", description = "Update the status of an interview step")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Interview step status updated successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = InterviewStepDto.class))),
        @ApiResponse(responseCode = "404", description = "Interview step not found")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<InterviewStepDto> updateInterviewStepStatus(
            @Parameter(description = "Interview step ID", required = true) @PathVariable Long id,
            @Parameter(description = "New status", required = true) @RequestParam StepStatus status) {
        log.debug("PATCH /interview-steps/{}/status - Updating interview step status to: {}", id, status);
        InterviewStepDto updatedInterviewStep = interviewStepService.updateStatus(id, status);
        return ResponseEntity.ok(updatedInterviewStep);
    }

    @Operation(summary = "Schedule interview step", description = "Schedule an interview step for a specific date and time")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Interview step scheduled successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = InterviewStepDto.class))),
        @ApiResponse(responseCode = "404", description = "Interview step not found")
    })
    @PatchMapping("/{id}/schedule")
    public ResponseEntity<InterviewStepDto> scheduleInterviewStep(
            @Parameter(description = "Interview step ID", required = true) @PathVariable Long id,
            @Parameter(description = "Scheduled date and time", required = true) 
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date) {
        log.debug("PATCH /interview-steps/{}/schedule - Scheduling interview step for: {}", id, date);
        InterviewStepDto updatedInterviewStep = interviewStepService.scheduleStep(id, date);
        return ResponseEntity.ok(updatedInterviewStep);
    }

    @Operation(summary = "Mark step as waiting feedback", description = "Mark an interview step as waiting for feedback")
    @PatchMapping("/{id}/waiting-feedback")
    public ResponseEntity<InterviewStepDto> markAsWaitingFeedback(
            @Parameter(description = "Interview step ID", required = true) @PathVariable Long id) {
        log.debug("PATCH /interview-steps/{}/waiting-feedback - Marking step as waiting feedback", id);
        InterviewStepDto updatedInterviewStep = interviewStepService.markAsWaitingFeedback(id);
        return ResponseEntity.ok(updatedInterviewStep);
    }

    @Operation(summary = "Mark step as validated", description = "Mark an interview step as validated")
    @PatchMapping("/{id}/validate")
    public ResponseEntity<InterviewStepDto> markAsValidated(
            @Parameter(description = "Interview step ID", required = true) @PathVariable Long id) {
        log.debug("PATCH /interview-steps/{}/validate - Marking step as validated", id);
        InterviewStepDto updatedInterviewStep = interviewStepService.markAsValidated(id);
        return ResponseEntity.ok(updatedInterviewStep);
    }

    @Operation(summary = "Mark step as failed", description = "Mark an interview step as failed")
    @PatchMapping("/{id}/fail")
    public ResponseEntity<InterviewStepDto> markAsFailed(
            @Parameter(description = "Interview step ID", required = true) @PathVariable Long id) {
        log.debug("PATCH /interview-steps/{}/fail - Marking step as failed", id);
        InterviewStepDto updatedInterviewStep = interviewStepService.markAsFailed(id);
        return ResponseEntity.ok(updatedInterviewStep);
    }

    @Operation(summary = "Mark step as canceled", description = "Mark an interview step as canceled")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<InterviewStepDto> markAsCanceled(
            @Parameter(description = "Interview step ID", required = true) @PathVariable Long id) {
        log.debug("PATCH /interview-steps/{}/cancel - Marking step as canceled", id);
        InterviewStepDto updatedInterviewStep = interviewStepService.markAsCanceled(id);
        return ResponseEntity.ok(updatedInterviewStep);
    }

    @Operation(summary = "Get kanban board", description = "Get kanban board data for a freelance")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved kanban board",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = KanbanBoardDto.class)))
    })
    @GetMapping("/kanban/{freelanceId}")
    public ResponseEntity<KanbanBoardDto> getKanbanBoard(
            @Parameter(description = "Freelance ID", required = true) @PathVariable Long freelanceId) {
        log.debug("GET /interview-steps/kanban/{} - Getting kanban board", freelanceId);
        KanbanBoardDto kanbanBoard = interviewStepService.getKanbanBoard(freelanceId);
        return ResponseEntity.ok(kanbanBoard);
    }

    @Operation(summary = "Transition project to next step", description = "Move a project from one interview step to the next")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully transitioned project",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = InterviewStepDto.class))),
        @ApiResponse(responseCode = "404", description = "Project or step not found")
    })
    @PostMapping("/transition")
    public ResponseEntity<InterviewStepDto> transitionProjectToNextStep(
            @Parameter(description = "Step transition details", required = true) @Valid @RequestBody StepTransitionDto transitionDto) {
        log.debug("POST /interview-steps/transition - Transitioning project from {} to {}",
            transitionDto.getFromStepTitle(), transitionDto.getToStepTitle());
        InterviewStepDto updatedStep = interviewStepService.transitionProjectToNextStep(transitionDto);
        return ResponseEntity.ok(updatedStep);
    }
}
