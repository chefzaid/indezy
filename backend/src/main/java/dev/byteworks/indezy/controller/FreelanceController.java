package dev.byteworks.indezy.controller;

import dev.byteworks.indezy.dto.FreelanceDto;
import dev.byteworks.indezy.service.FreelanceService;
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
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/freelances")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
@Tag(name = "Freelances", description = "Freelance profile management operations")
public class FreelanceController {

    private final FreelanceService freelanceService;

    @Operation(summary = "Get all freelances", description = "Retrieve a list of all freelance profiles")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved freelances",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FreelanceDto.class)))
    })
    @GetMapping
    public ResponseEntity<List<FreelanceDto>> getAllFreelances() {
        log.debug("GET /freelances - Getting all freelances");
        List<FreelanceDto> freelances = freelanceService.findAll();
        return ResponseEntity.ok(freelances);
    }

    @Operation(summary = "Get freelance by ID", description = "Retrieve a specific freelance profile by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved freelance",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FreelanceDto.class))),
        @ApiResponse(responseCode = "404", description = "Freelance not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<FreelanceDto> getFreelanceById(
            @Parameter(description = "Freelance ID", required = true) @PathVariable Long id) {
        log.debug("GET /freelances/{} - Getting freelance by id", id);
        FreelanceDto freelance = freelanceService.findById(id);
        return ResponseEntity.ok(freelance);
    }

    @Operation(summary = "Get freelance with projects", description = "Retrieve a freelance profile with associated projects")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved freelance with projects",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FreelanceDto.class))),
        @ApiResponse(responseCode = "404", description = "Freelance not found")
    })
    @GetMapping("/{id}/with-projects")
    public ResponseEntity<FreelanceDto> getFreelanceByIdWithProjects(
            @Parameter(description = "Freelance ID", required = true) @PathVariable Long id) {
        log.debug("GET /freelances/{}/with-projects - Getting freelance with projects", id);
        FreelanceDto freelance = freelanceService.findByIdWithProjects(id);
        return ResponseEntity.ok(freelance);
    }

    @Operation(summary = "Get freelance by email", description = "Retrieve a freelance profile by email address")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved freelance",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FreelanceDto.class))),
        @ApiResponse(responseCode = "404", description = "Freelance not found")
    })
    @GetMapping("/by-email")
    public ResponseEntity<FreelanceDto> getFreelanceByEmail(
            @Parameter(description = "Email address", required = true) @RequestParam String email) {
        log.debug("GET /freelances/by-email?email={} - Getting freelance by email", email);
        FreelanceDto freelance = freelanceService.findByEmail(email);
        return ResponseEntity.ok(freelance);
    }

    @Operation(summary = "Create freelance", description = "Create a new freelance profile")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Successfully created freelance",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FreelanceDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    @PostMapping
    public ResponseEntity<FreelanceDto> createFreelance(@Valid @RequestBody FreelanceDto freelanceDto) {
        log.debug("POST /freelances - Creating new freelance");
        FreelanceDto createdFreelance = freelanceService.create(freelanceDto);
        return new ResponseEntity<>(createdFreelance, HttpStatus.CREATED);
    }

    @Operation(summary = "Update freelance", description = "Update an existing freelance profile")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully updated freelance",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = FreelanceDto.class))),
        @ApiResponse(responseCode = "404", description = "Freelance not found"),
        @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<FreelanceDto> updateFreelance(
            @Parameter(description = "Freelance ID", required = true) @PathVariable Long id,
            @Valid @RequestBody FreelanceDto freelanceDto) {
        log.debug("PUT /freelances/{} - Updating freelance", id);
        FreelanceDto updatedFreelance = freelanceService.update(id, freelanceDto);
        return ResponseEntity.ok(updatedFreelance);
    }

    @Operation(summary = "Delete freelance", description = "Delete a freelance profile")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Successfully deleted freelance"),
        @ApiResponse(responseCode = "404", description = "Freelance not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFreelance(
            @Parameter(description = "Freelance ID", required = true) @PathVariable Long id) {
        log.debug("DELETE /freelances/{} - Deleting freelance", id);
        freelanceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Check email exists", description = "Check if a freelance with the given email already exists")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully checked email existence",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Boolean.class)))
    })
    @GetMapping("/exists")
    public ResponseEntity<Boolean> checkEmailExists(
            @Parameter(description = "Email address to check", required = true) @RequestParam String email) {
        log.debug("GET /freelances/exists?email={} - Checking if email exists", email);
        boolean exists = freelanceService.existsByEmail(email);
        return ResponseEntity.ok(exists);
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> updatePassword(@PathVariable Long id, 
                                             @RequestBody String newPassword) {
        log.debug("PATCH /freelances/{}/password - Updating password", id);
        freelanceService.updatePassword(id, newPassword);
        return ResponseEntity.ok().build();
    }
}
