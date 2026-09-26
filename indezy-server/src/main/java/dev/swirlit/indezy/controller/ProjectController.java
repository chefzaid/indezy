package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.dto.KanbanBoardDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.DashboardStatsService;
import dev.swirlit.indezy.service.ProjectExportService;
import dev.swirlit.indezy.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Opportunities, the Kanban pipeline and the dashboard")
public class ProjectController {

    private final ProjectService projectService;
    private final DashboardStatsService dashboardStatsService;
    private final ProjectExportService projectExportService;
    private final AccessGuard accessGuard;

    @Operation(summary = "List the caller's projects")
    @GetMapping
    public List<ProjectDto> getAllProjects() {
        return accessGuard.currentFreelanceId()
            .map(projectService::findByFreelanceId)
            .orElseGet(projectService::findAll);
    }

    @Operation(summary = "List the projects of a workspace")
    @GetMapping("/by-freelance/{freelanceId}")
    public List<ProjectDto> getProjectsByFreelanceId(@PathVariable Long freelanceId) {
        accessGuard.requireFreelance(freelanceId);
        return projectService.findByFreelanceId(freelanceId);
    }

    @Operation(summary = "List the projects of a client")
    @GetMapping("/by-client/{clientId}")
    public List<ProjectDto> getProjectsByClientId(@PathVariable Long clientId) {
        accessGuard.requireClient(clientId);
        return projectService.findByClientId(clientId);
    }

    @Operation(summary = "Get a project")
    @GetMapping("/{id}")
    public ProjectDto getProjectById(@PathVariable Long id) {
        accessGuard.requireProject(id);
        return projectService.findById(id);
    }

    @Operation(summary = "Create a project",
        description = "Without a season, the project joins the season running today (if any)")
    @PostMapping
    public ResponseEntity<ProjectDto> createProject(@Valid @RequestBody ProjectDto projectDto) {
        projectDto.setFreelanceId(accessGuard.resolveFreelanceId(projectDto.getFreelanceId()));
        accessGuard.requireClient(projectDto.getClientId());
        requireReferences(projectDto);
        return new ResponseEntity<>(projectService.create(projectDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Replace a project")
    @PutMapping("/{id}")
    public ProjectDto updateProject(@PathVariable Long id, @Valid @RequestBody ProjectDto projectDto) {
        accessGuard.requireProject(id);
        accessGuard.requireClientIfPresent(projectDto.getClientId());
        requireReferences(projectDto);
        return projectService.update(id, projectDto);
    }

    @Operation(summary = "Delete a project", description = "Its interview steps and journal notes go with it")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        accessGuard.requireProject(id);
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Move a project to another pipeline stage")
    @PatchMapping("/{id}/status")
    public ProjectDto updateProjectStatus(@PathVariable Long id,
                                          @RequestParam ProjectStatus status,
                                          @RequestParam(required = false) LostReason lostReason) {
        accessGuard.requireProject(id);
        return projectService.updateStatus(id, status, lostReason);
    }

    @Operation(summary = "Pin or unpin a project at the top of its Kanban column")
    @PatchMapping("/{id}/favorite")
    public ProjectDto toggleFavorite(@PathVariable Long id) {
        accessGuard.requireProject(id);
        return projectService.toggleFavorite(id);
    }

    @Operation(summary = "Rename a skill tag on every project of a workspace")
    @PutMapping("/by-freelance/{freelanceId}/tags/rename")
    public int renameTag(@PathVariable Long freelanceId, @RequestBody Map<String, String> body) {
        accessGuard.requireFreelance(freelanceId);
        return projectService.renameTag(freelanceId, body.get("from"), body.get("to"));
    }

    @Operation(summary = "Kanban board", description = "Projects grouped by status, optionally for one season")
    @GetMapping("/kanban/{freelanceId}")
    public KanbanBoardDto getKanbanBoard(@PathVariable Long freelanceId,
                                         @RequestParam(required = false) Long seasonId) {
        accessGuard.requireFreelance(freelanceId);
        accessGuard.requireSeasonIfPresent(seasonId);
        return projectService.getKanbanBoard(freelanceId, seasonId);
    }

    @Operation(summary = "Persist the manual order of a Kanban column")
    @PutMapping("/kanban/{freelanceId}/reorder")
    public ResponseEntity<Void> reorderKanbanColumn(@PathVariable Long freelanceId,
                                                    @RequestBody List<Long> orderedProjectIds) {
        accessGuard.requireFreelance(freelanceId);
        projectService.reorderKanbanColumn(freelanceId, orderedProjectIds);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Dashboard statistics", description = "For all time, or for one season")
    @GetMapping("/stats/dashboard/{freelanceId}")
    public DashboardStatsDto getDashboardStats(@PathVariable Long freelanceId,
                                               @RequestParam(required = false) Long seasonId) {
        accessGuard.requireFreelance(freelanceId);
        accessGuard.requireSeasonIfPresent(seasonId);
        return dashboardStatsService.getDashboardStats(freelanceId, seasonId);
    }

    @Operation(summary = "Accountant CSV summary", description = "All projects, or those of one year")
    @GetMapping("/export/csv/{freelanceId}")
    public ResponseEntity<String> exportYearlySummary(@PathVariable Long freelanceId,
                                                      @RequestParam(required = false) Integer year) {
        accessGuard.requireFreelance(freelanceId);
        String filename = "indezy-summary-" + (year != null ? year : "all") + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(projectExportService.buildYearlySummaryCsv(freelanceId, year));
    }

    /** Optional references of a project must belong to the caller's workspace. */
    private void requireReferences(ProjectDto projectDto) {
        accessGuard.requireClientIfPresent(projectDto.getMiddlemanId());
        accessGuard.requireSourceIfPresent(projectDto.getSourceId());
        accessGuard.requireSeasonIfPresent(projectDto.getSeasonId());
    }
}
