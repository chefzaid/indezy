package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.SourceDto;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.SourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sources")
@RequiredArgsConstructor
@Tag(name = "Sources", description = "Where opportunities come from")
public class SourceController {

    private final SourceService sourceService;
    private final AccessGuard accessGuard;

    @Operation(summary = "List the caller's sources")
    @GetMapping
    public List<SourceDto> getAllSources() {
        return accessGuard.currentFreelanceId()
            .map(sourceService::findByFreelanceId)
            .orElseGet(sourceService::findAll);
    }

    @Operation(summary = "List the sources of a workspace")
    @GetMapping("/by-freelance/{freelanceId}")
    public List<SourceDto> getSourcesByFreelanceId(@PathVariable Long freelanceId) {
        accessGuard.requireFreelance(freelanceId);
        return sourceService.findByFreelanceId(freelanceId);
    }

    @Operation(summary = "Get a source")
    @GetMapping("/{id}")
    public SourceDto getSourceById(@PathVariable Long id) {
        accessGuard.requireSource(id);
        return sourceService.findById(id);
    }

    @Operation(summary = "Create a source")
    @PostMapping
    public ResponseEntity<SourceDto> createSource(@Valid @RequestBody SourceDto sourceDto) {
        sourceDto.setFreelanceId(accessGuard.resolveFreelanceId(sourceDto.getFreelanceId()));
        return new ResponseEntity<>(sourceService.create(sourceDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Update a source")
    @PutMapping("/{id}")
    public SourceDto updateSource(@PathVariable Long id, @Valid @RequestBody SourceDto sourceDto) {
        accessGuard.requireSource(id);
        sourceDto.setFreelanceId(accessGuard.resolveFreelanceId(sourceDto.getFreelanceId()));
        return sourceService.update(id, sourceDto);
    }

    @Operation(summary = "Delete a source", description = "Its opportunities are kept, without a source")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSource(@PathVariable Long id) {
        accessGuard.requireSource(id);
        sourceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
