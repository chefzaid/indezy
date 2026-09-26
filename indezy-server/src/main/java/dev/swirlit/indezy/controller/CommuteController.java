package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.CommuteInfoDto;
import dev.swirlit.indezy.dto.ProjectCommuteDto;
import dev.swirlit.indezy.model.enums.TravelMode;
import dev.swirlit.indezy.service.AccessGuard;
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
@Tag(name = "Commute", description = "Commute time estimation and project sorting by distance")
public class CommuteController {

    private final CommuteService commuteService;
    private final AccessGuard accessGuard;

    @Operation(summary = "Get projects sorted by commute time",
            description = "Returns all projects for a freelancer sorted by commute time from their home address to the client location")
    @GetMapping("/projects/{freelanceId}")
    public ResponseEntity<List<ProjectCommuteDto>> getProjectsSortedByCommute(
            @PathVariable Long freelanceId,
            @RequestParam(defaultValue = "DRIVING") TravelMode travelMode) {
        log.debug("GET /commute/projects/{} - mode={}", freelanceId, travelMode);
        accessGuard.requireFreelance(freelanceId);
        List<ProjectCommuteDto> results = commuteService.getProjectsSortedByCommute(freelanceId, travelMode);
        return ResponseEntity.ok(results);
    }

}
