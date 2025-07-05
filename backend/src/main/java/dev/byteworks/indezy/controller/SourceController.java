package dev.byteworks.indezy.controller;

import dev.byteworks.indezy.dto.SourceDto;
import dev.byteworks.indezy.model.enums.SourceType;
import dev.byteworks.indezy.service.SourceService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sources")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
@Tag(name = "Sources", description = "Source management operations")
public class SourceController {

    private final SourceService sourceService;

    @Operation(summary = "Get all sources", description = "Retrieve a list of all sources")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved sources",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = SourceDto.class)))
    })
    @GetMapping
    public ResponseEntity<List<SourceDto>> getAllSources() {
        log.debug("GET /sources - Getting all sources");
        List<SourceDto> sources = sourceService.findAll();
        return ResponseEntity.ok(sources);
    }

    @Operation(summary = "Get source by ID", description = "Retrieve a specific source by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved source",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = SourceDto.class))),
        @ApiResponse(responseCode = "404", description = "Source not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<SourceDto> getSourceById(
            @Parameter(description = "Source ID", required = true) @PathVariable Long id) {
        log.debug("GET /sources/{} - Getting source by id", id);
        SourceDto source = sourceService.findById(id);
        return ResponseEntity.ok(source);
    }

    @Operation(summary = "Create new source", description = "Create a new source with the provided details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Source created successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = SourceDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid source data")
    })
    @PostMapping
    public ResponseEntity<SourceDto> createSource(
            @Parameter(description = "Source details", required = true) @Valid @RequestBody SourceDto sourceDto) {
        log.debug("POST /sources - Creating new source");
        SourceDto createdSource = sourceService.create(sourceDto);
        return new ResponseEntity<>(createdSource, HttpStatus.CREATED);
    }

    @Operation(summary = "Update source", description = "Update an existing source with the provided details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Source updated successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = SourceDto.class))),
        @ApiResponse(responseCode = "404", description = "Source not found"),
        @ApiResponse(responseCode = "400", description = "Invalid source data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<SourceDto> updateSource(
            @Parameter(description = "Source ID", required = true) @PathVariable Long id,
            @Parameter(description = "Updated source details", required = true) @Valid @RequestBody SourceDto sourceDto) {
        log.debug("PUT /sources/{} - Updating source", id);
        SourceDto updatedSource = sourceService.update(id, sourceDto);
        return ResponseEntity.ok(updatedSource);
    }

    @Operation(summary = "Delete source", description = "Delete a source by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Source deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Source not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSource(
            @Parameter(description = "Source ID", required = true) @PathVariable Long id) {
        log.debug("DELETE /sources/{} - Deleting source", id);
        sourceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get sources by freelance ID", description = "Retrieve all sources for a specific freelance")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved sources",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = SourceDto.class)))
    })
    @GetMapping("/by-freelance/{freelanceId}")
    public ResponseEntity<List<SourceDto>> getSourcesByFreelanceId(
            @Parameter(description = "Freelance ID", required = true) @PathVariable Long freelanceId) {
        log.debug("GET /sources/by-freelance/{} - Getting sources by freelance id", freelanceId);
        List<SourceDto> sources = sourceService.findByFreelanceId(freelanceId);
        return ResponseEntity.ok(sources);
    }

    @Operation(summary = "Get sources by type", description = "Filter sources by type for a specific freelance")
    @GetMapping("/by-freelance/{freelanceId}/type/{type}")
    public ResponseEntity<List<SourceDto>> getSourcesByType(
            @PathVariable Long freelanceId,
            @PathVariable SourceType type) {
        log.debug("GET /sources/by-freelance/{}/type/{} - Getting sources by type", freelanceId, type);
        List<SourceDto> sources = sourceService.findByFreelanceIdAndType(freelanceId, type);
        return ResponseEntity.ok(sources);
    }

    @Operation(summary = "Get sources by listing status", description = "Filter sources by listing status")
    @GetMapping("/by-freelance/{freelanceId}/listing/{isListing}")
    public ResponseEntity<List<SourceDto>> getSourcesByListingStatus(
            @PathVariable Long freelanceId,
            @PathVariable Boolean isListing) {
        log.debug("GET /sources/by-freelance/{}/listing/{} - Getting sources by listing status", freelanceId, isListing);
        List<SourceDto> sources = sourceService.findByFreelanceIdAndIsListing(freelanceId, isListing);
        return ResponseEntity.ok(sources);
    }

    @Operation(summary = "Get sources by minimum popularity rating", description = "Filter sources by minimum popularity rating")
    @GetMapping("/by-freelance/{freelanceId}/popularity/{minRating}")
    public ResponseEntity<List<SourceDto>> getSourcesByPopularityRating(
            @PathVariable Long freelanceId,
            @PathVariable Integer minRating) {
        log.debug("GET /sources/by-freelance/{}/popularity/{} - Getting sources by popularity rating", freelanceId, minRating);
        List<SourceDto> sources = sourceService.findByFreelanceIdAndPopularityRatingGreaterThanEqual(freelanceId, minRating);
        return ResponseEntity.ok(sources);
    }

    @Operation(summary = "Get sources by minimum usefulness rating", description = "Filter sources by minimum usefulness rating")
    @GetMapping("/by-freelance/{freelanceId}/usefulness/{minRating}")
    public ResponseEntity<List<SourceDto>> getSourcesByUsefulnessRating(
            @PathVariable Long freelanceId,
            @PathVariable Integer minRating) {
        log.debug("GET /sources/by-freelance/{}/usefulness/{} - Getting sources by usefulness rating", freelanceId, minRating);
        List<SourceDto> sources = sourceService.findByFreelanceIdAndUsefulnessRatingGreaterThanEqual(freelanceId, minRating);
        return ResponseEntity.ok(sources);
    }

    @Operation(summary = "Get source with projects", description = "Retrieve a source with all associated projects")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved source with projects",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = SourceDto.class))),
        @ApiResponse(responseCode = "404", description = "Source not found")
    })
    @GetMapping("/{id}/with-projects")
    public ResponseEntity<SourceDto> getSourceWithProjects(
            @Parameter(description = "Source ID", required = true) @PathVariable Long id) {
        log.debug("GET /sources/{}/with-projects - Getting source with projects", id);
        SourceDto source = sourceService.findByIdWithProjects(id);
        return ResponseEntity.ok(source);
    }

    @Operation(summary = "Get average ratings", description = "Get average popularity and usefulness ratings for a freelance's sources")
    @GetMapping("/by-freelance/{freelanceId}/average-ratings")
    public ResponseEntity<Map<String, Double>> getAverageRatings(@PathVariable Long freelanceId) {
        log.debug("GET /sources/by-freelance/{}/average-ratings - Getting average ratings", freelanceId);
        
        Double avgPopularity = sourceService.getAveragePopularityRating(freelanceId);
        Double avgUsefulness = sourceService.getAverageUsefulnessRating(freelanceId);
        
        Map<String, Double> ratings = new HashMap<>();
        ratings.put("averagePopularityRating", avgPopularity);
        ratings.put("averageUsefulnessRating", avgUsefulness);
        
        return ResponseEntity.ok(ratings);
    }

    @Operation(summary = "Get distinct source types", description = "Get all unique source types for a freelance")
    @GetMapping("/by-freelance/{freelanceId}/types")
    public ResponseEntity<List<SourceType>> getDistinctTypes(@PathVariable Long freelanceId) {
        log.debug("GET /sources/by-freelance/{}/types - Getting distinct types", freelanceId);
        List<SourceType> types = sourceService.findDistinctTypesByFreelanceId(freelanceId);
        return ResponseEntity.ok(types);
    }
}
