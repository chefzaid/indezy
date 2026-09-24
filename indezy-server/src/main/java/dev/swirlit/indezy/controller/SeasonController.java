package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.SeasonDto;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.SeasonService;
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
@RequestMapping("/seasons")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Seasons", description = "Job-hunting seasons, each with its own pipeline and dashboard")
public class SeasonController {

    private final SeasonService seasonService;
    private final AccessGuard accessGuard;

    @Operation(summary = "List seasons", description = "Seasons of a freelance workspace, most recent first")
    @GetMapping("/by-freelance/{freelanceId}")
    public ResponseEntity<List<SeasonDto>> getSeasons(@PathVariable Long freelanceId) {
        log.debug("GET /seasons/by-freelance/{}", freelanceId);
        accessGuard.requireFreelance(freelanceId);
        return ResponseEntity.ok(seasonService.findByFreelanceId(freelanceId));
    }

    @Operation(summary = "Get season")
    @GetMapping("/{id}")
    public ResponseEntity<SeasonDto> getSeason(@PathVariable Long id) {
        log.debug("GET /seasons/{}", id);
        accessGuard.requireSeason(id);
        return ResponseEntity.ok(seasonService.findById(id));
    }

    @Operation(summary = "Create season",
        description = "Create a season; unassigned opportunities created within its dates join it")
    @PostMapping
    public ResponseEntity<SeasonDto> createSeason(@Valid @RequestBody SeasonDto seasonDto) {
        log.debug("POST /seasons - {}", seasonDto.getName());
        seasonDto.setFreelanceId(accessGuard.resolveFreelanceId(seasonDto.getFreelanceId()));
        return new ResponseEntity<>(seasonService.create(seasonDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Update season")
    @PutMapping("/{id}")
    public ResponseEntity<SeasonDto> updateSeason(@PathVariable Long id, @Valid @RequestBody SeasonDto seasonDto) {
        log.debug("PUT /seasons/{}", id);
        accessGuard.requireSeason(id);
        return ResponseEntity.ok(seasonService.update(id, seasonDto));
    }

    @Operation(summary = "Delete season", description = "Delete a season; its opportunities are kept")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSeason(@PathVariable Long id) {
        log.debug("DELETE /seasons/{}", id);
        accessGuard.requireSeason(id);
        seasonService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
