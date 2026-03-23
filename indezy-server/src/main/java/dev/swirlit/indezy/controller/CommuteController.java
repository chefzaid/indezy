package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.CommuteInfoDto;
import dev.swirlit.indezy.dto.ProjectCommuteDto;
import dev.swirlit.indezy.model.enums.TravelMode;
import dev.swirlit.indezy.service.CommuteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/commute")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
@Tag(name = "Commute", description = "Commute time estimation and project sorting by distance")
public class CommuteController {

    private final CommuteService commuteService;

    @Operation(summary = "Get projects sorted by commute time",
            description = "Returns all projects for a freelancer sorted by commute time from their home address to the client location")
    @GetMapping("/projects/{freelanceId}")
    public ResponseEntity<List<ProjectCommuteDto>> getProjectsSortedByCommute(
            @PathVariable Long freelanceId,
            @RequestParam(defaultValue = "DRIVING") TravelMode travelMode) {
        log.debug("GET /commute/projects/{} - mode={}", freelanceId, travelMode);
        List<ProjectCommuteDto> results = commuteService.getProjectsSortedByCommute(freelanceId, travelMode);
        return ResponseEntity.ok(results);
    }

    @Operation(summary = "Get commute info for a single project",
            description = "Returns commute time details from the freelancer's home to a specific project's client location")
    @GetMapping("/projects/{freelanceId}/{projectId}")
    public ResponseEntity<CommuteInfoDto> getCommuteForProject(
            @PathVariable Long freelanceId,
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "DRIVING") TravelMode travelMode) {
        log.debug("GET /commute/projects/{}/{} - mode={}", freelanceId, projectId, travelMode);
        CommuteInfoDto result = commuteService.getCommuteForProject(freelanceId, projectId, travelMode);
        return ResponseEntity.ok(result);
    }
}
