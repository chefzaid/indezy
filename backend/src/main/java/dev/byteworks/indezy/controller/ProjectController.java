package dev.byteworks.indezy.controller;

import dev.byteworks.indezy.dto.ProjectDto;
import dev.byteworks.indezy.model.enums.WorkMode;
import dev.byteworks.indezy.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<List<ProjectDto>> getAllProjects() {
        log.debug("GET /projects - Getting all projects");
        List<ProjectDto> projects = projectService.findAll();
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectDto> getProjectById(@PathVariable Long id) {
        log.debug("GET /projects/{} - Getting project by id", id);
        ProjectDto project = projectService.findById(id);
        return ResponseEntity.ok(project);
    }

    @GetMapping("/{id}/with-steps")
    public ResponseEntity<ProjectDto> getProjectByIdWithSteps(@PathVariable Long id) {
        log.debug("GET /projects/{}/with-steps - Getting project with steps", id);
        ProjectDto project = projectService.findByIdWithSteps(id);
        return ResponseEntity.ok(project);
    }

    @GetMapping("/by-freelance/{freelanceId}")
    public ResponseEntity<List<ProjectDto>> getProjectsByFreelanceId(@PathVariable Long freelanceId) {
        log.debug("GET /projects/by-freelance/{} - Getting projects by freelance id", freelanceId);
        List<ProjectDto> projects = projectService.findByFreelanceId(freelanceId);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/by-client/{clientId}")
    public ResponseEntity<List<ProjectDto>> getProjectsByClientId(@PathVariable Long clientId) {
        log.debug("GET /projects/by-client/{} - Getting projects by client id", clientId);
        List<ProjectDto> projects = projectService.findByClientId(clientId);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/by-freelance/{freelanceId}/filtered")
    public ResponseEntity<List<ProjectDto>> getProjectsByFreelanceIdWithFilters(
            @PathVariable Long freelanceId,
            @RequestParam(required = false) Integer minRate,
            @RequestParam(required = false) Integer maxRate,
            @RequestParam(required = false) WorkMode workMode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDateAfter,
            @RequestParam(required = false) String techStack) {
        
        log.debug("GET /projects/by-freelance/{}/filtered - Getting filtered projects", freelanceId);
        List<ProjectDto> projects = projectService.findByFreelanceIdAndFilters(
            freelanceId, minRate, maxRate, workMode, startDateAfter, techStack);
        return ResponseEntity.ok(projects);
    }

    @PostMapping
    public ResponseEntity<ProjectDto> createProject(@Valid @RequestBody ProjectDto projectDto) {
        log.debug("POST /projects - Creating new project");
        ProjectDto createdProject = projectService.create(projectDto);
        return new ResponseEntity<>(createdProject, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectDto> updateProject(@PathVariable Long id, 
                                                   @Valid @RequestBody ProjectDto projectDto) {
        log.debug("PUT /projects/{} - Updating project", id);
        ProjectDto updatedProject = projectService.update(id, projectDto);
        return ResponseEntity.ok(updatedProject);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        log.debug("DELETE /projects/{} - Deleting project", id);
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats/average-rate/{freelanceId}")
    public ResponseEntity<Double> getAverageDailyRate(@PathVariable Long freelanceId) {
        log.debug("GET /projects/stats/average-rate/{} - Getting average daily rate", freelanceId);
        Double averageRate = projectService.getAverageDailyRateByFreelanceId(freelanceId);
        return ResponseEntity.ok(averageRate);
    }

    @GetMapping("/stats/count/{freelanceId}")
    public ResponseEntity<Long> getProjectCount(@PathVariable Long freelanceId) {
        log.debug("GET /projects/stats/count/{} - Getting project count", freelanceId);
        Long count = projectService.countByFreelanceId(freelanceId);
        return ResponseEntity.ok(count);
    }
}
