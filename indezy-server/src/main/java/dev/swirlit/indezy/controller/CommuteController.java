package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.CommuteInfoDto;
import dev.swirlit.indezy.dto.OpportunityMapDto;
import dev.swirlit.indezy.dto.ProjectCommuteDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.TravelMode;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.CommuteService;
import dev.swirlit.indezy.service.OpportunityMapService;
import dev.swirlit.indezy.service.ProfessionalKilometresService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final ProfessionalKilometresService kilometresService;
    private final OpportunityMapService mapService;

    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

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

    @Operation(summary = "Get one project's commute",
            description = "Returns the trip from the freelancer's home to the project's client by car, then by public transport")
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<CommuteInfoDto>> getProjectCommute(@PathVariable Long projectId) {
        accessGuard.requireProject(projectId);
        return ResponseEntity.ok(commuteService.getProjectCommute(projectId));
    }

    @Operation(summary = "Export the year's professional kilometres",
            description = "Days on site of the validated activity reports times each mission's round trip by car, as CSV")
    @GetMapping("/kilometres/{freelanceId}/{year}")
    public ResponseEntity<String> exportKilometres(@PathVariable Long freelanceId, @PathVariable int year,
                                                   @RequestParam(defaultValue = "FR") DocumentLanguage language) {
        accessGuard.requireFreelance(freelanceId);
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new IllegalArgumentException("Year out of range");
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"kilometres-" + year + ".csv\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(kilometresService.csv(freelanceId, year, language));
    }

    @Operation(summary = "The opportunities on a map", description = "The home address and each opportunity's place, located by the address search")
    @GetMapping("/map/{freelanceId}")
    public OpportunityMapDto map(@PathVariable Long freelanceId) {
        accessGuard.requireFreelance(freelanceId);
        return mapService.map(freelanceId);
    }
}
