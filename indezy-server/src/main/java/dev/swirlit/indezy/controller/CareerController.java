package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.CareerDtos;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.CareerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The profile's past positions and education. Another workspace's entry answers 404. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Experience and education", description = "Past positions and schools, printed in the dossier de compétences")
public class CareerController {

    private final CareerService career;
    private final AccessGuard accessGuard;

    @Operation(summary = "The profile's past positions, the running and latest first")
    @GetMapping("/users/experiences")
    public List<CareerDtos.Experience> experiences() {
        return career.experiences(workspace());
    }

    @Operation(summary = "Add a past position")
    @PostMapping("/users/experiences")
    @ResponseStatus(HttpStatus.CREATED)
    public CareerDtos.Experience createExperience(@Valid @RequestBody CareerDtos.ExperienceDetails details) {
        return career.createExperience(workspace(), details);
    }

    @Operation(summary = "Change a past position")
    @PutMapping("/users/experiences/{id}")
    public CareerDtos.Experience updateExperience(@PathVariable Long id, @Valid @RequestBody CareerDtos.ExperienceDetails details) {
        return career.updateExperience(workspace(), id, details);
    }

    @Operation(summary = "Delete a past position")
    @DeleteMapping("/users/experiences/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExperience(@PathVariable Long id) {
        career.deleteExperience(workspace(), id);
    }

    @Operation(summary = "The profile's schools and degrees, the latest first")
    @GetMapping("/users/educations")
    public List<CareerDtos.Education> educations() {
        return career.educations(workspace());
    }

    @Operation(summary = "Add a school or degree")
    @PostMapping("/users/educations")
    @ResponseStatus(HttpStatus.CREATED)
    public CareerDtos.Education createEducation(@Valid @RequestBody CareerDtos.EducationDetails details) {
        return career.createEducation(workspace(), details);
    }

    @Operation(summary = "Change a school or degree")
    @PutMapping("/users/educations/{id}")
    public CareerDtos.Education updateEducation(@PathVariable Long id, @Valid @RequestBody CareerDtos.EducationDetails details) {
        return career.updateEducation(workspace(), id, details);
    }

    @Operation(summary = "Delete a school or degree")
    @DeleteMapping("/users/educations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEducation(@PathVariable Long id) {
        career.deleteEducation(workspace(), id);
    }

    private Long workspace() {
        return accessGuard.requireWorkspace(null);
    }
}
