package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.InterviewStepDto;
import dev.swirlit.indezy.dto.PipelineTemplateDto;
import dev.swirlit.indezy.dto.PipelineTemplateRequest;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.PipelineTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pipeline-templates")
@RequiredArgsConstructor
@Tag(name = "Pipeline templates", description = "Recruitment processes saved from an opportunity and reused")
public class PipelineTemplateController {

    private final PipelineTemplateService templateService;
    private final AccessGuard accessGuard;

    @Operation(summary = "The workspace's templates, by name")
    @GetMapping
    public List<PipelineTemplateDto> list(@RequestParam(required = false) Long freelanceId) {
        return templateService.list(accessGuard.requireWorkspace(freelanceId));
    }

    @Operation(summary = "Save an opportunity's steps as a template, for a client or ESN or for anyone")
    @PostMapping
    public ResponseEntity<PipelineTemplateDto> create(@Valid @RequestBody PipelineTemplateRequest request) {
        accessGuard.requireProject(request.projectId());
        accessGuard.requireClientIfPresent(request.clientId());
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createFromProject(request));
    }

    @Operation(summary = "Add a template's steps to an opportunity, to plan")
    @PostMapping("/{id}/apply/{projectId}")
    public List<InterviewStepDto> apply(@PathVariable Long id, @PathVariable Long projectId) {
        accessGuard.requirePipelineTemplate(id);
        accessGuard.requireProject(projectId);
        return templateService.apply(id, projectId);
    }

    @Operation(summary = "Delete a template; the steps it created stay")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        accessGuard.requirePipelineTemplate(id);
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
