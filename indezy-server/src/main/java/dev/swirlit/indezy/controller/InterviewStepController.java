package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.InterviewStepDto;
import dev.swirlit.indezy.model.enums.StepStatus;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.InterviewStepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/interview-steps")
@RequiredArgsConstructor
@Tag(name = "Interview Steps", description = "Recruitment steps of an opportunity")
public class InterviewStepController {

    private final InterviewStepService interviewStepService;
    private final AccessGuard accessGuard;

    @Operation(summary = "List the caller's interview steps")
    @GetMapping
    public List<InterviewStepDto> getAllInterviewSteps() {
        return accessGuard.currentFreelanceId()
            .map(freelanceId -> interviewStepService.findByFreelanceIdAndStatus(freelanceId, null))
            .orElseGet(interviewStepService::findAll);
    }

    @Operation(summary = "List the interview steps of a workspace", description = "Optionally with one status")
    @GetMapping("/by-freelance/{freelanceId}")
    public List<InterviewStepDto> getInterviewStepsByFreelanceIdAndStatus(
            @PathVariable Long freelanceId,
            @RequestParam(required = false) StepStatus status) {
        accessGuard.requireFreelance(freelanceId);
        return interviewStepService.findByFreelanceIdAndStatus(freelanceId, status);
    }

    @Operation(summary = "List the interview steps of a project, by date")
    @GetMapping("/by-project/{projectId}/ordered")
    public List<InterviewStepDto> getInterviewStepsByProjectIdOrderByDate(@PathVariable Long projectId) {
        accessGuard.requireProject(projectId);
        return interviewStepService.findByProjectIdOrderByDate(projectId);
    }

    @Operation(summary = "Get an interview step")
    @GetMapping("/{id}")
    public InterviewStepDto getInterviewStepById(@PathVariable Long id) {
        accessGuard.requireInterviewStep(id);
        return interviewStepService.findById(id);
    }

    @Operation(summary = "Create an interview step")
    @PostMapping
    public ResponseEntity<InterviewStepDto> createInterviewStep(@Valid @RequestBody InterviewStepDto interviewStepDto) {
        accessGuard.requireProject(interviewStepDto.getProjectId());
        return new ResponseEntity<>(interviewStepService.create(interviewStepDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Update an interview step")
    @PutMapping("/{id}")
    public InterviewStepDto updateInterviewStep(@PathVariable Long id,
                                                @Valid @RequestBody InterviewStepDto interviewStepDto) {
        accessGuard.requireInterviewStep(id);
        return interviewStepService.update(id, interviewStepDto);
    }

    @Operation(summary = "Change the status of an interview step")
    @PatchMapping("/{id}/status")
    public InterviewStepDto updateInterviewStepStatus(@PathVariable Long id, @RequestParam StepStatus status) {
        accessGuard.requireInterviewStep(id);
        return interviewStepService.updateStatus(id, status);
    }

    @Operation(summary = "Delete an interview step")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInterviewStep(@PathVariable Long id) {
        accessGuard.requireInterviewStep(id);
        interviewStepService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
