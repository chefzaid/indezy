package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.FreelanceDto;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.FreelanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Freelance profiles; a workspace is created with its account at registration or first SSO login. */
@RestController
@RequestMapping("/freelances")
@RequiredArgsConstructor
@Tag(name = "Freelances", description = "Freelance profile (the workspace owner)")
public class FreelanceController {

    private final FreelanceService freelanceService;
    private final AccessGuard accessGuard;

    @Operation(summary = "The caller's freelance profile, as a list")
    @GetMapping
    public List<FreelanceDto> getAllFreelances() {
        return accessGuard.currentFreelanceId()
            .map(freelanceId -> List.of(freelanceService.findById(freelanceId)))
            .orElseGet(freelanceService::findAll);
    }

    @Operation(summary = "Get a freelance profile")
    @GetMapping("/{id}")
    public FreelanceDto getFreelanceById(@PathVariable Long id) {
        accessGuard.requireFreelance(id);
        return freelanceService.findById(id);
    }

    @Operation(summary = "Update a freelance profile")
    @PutMapping("/{id}")
    public FreelanceDto updateFreelance(@PathVariable Long id, @Valid @RequestBody FreelanceDto freelanceDto) {
        accessGuard.requireFreelance(id);
        return freelanceService.update(id, freelanceDto);
    }
}
