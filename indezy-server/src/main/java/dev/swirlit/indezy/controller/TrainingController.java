package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.TrainingDtos;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.TrainingService;
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

/** The workspace's training record. Another workspace's training answers 404. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Trainings", description = "Trainings, their certifications and the funding asked for them")
public class TrainingController {

    private final TrainingService trainings;
    private final AccessGuard accessGuard;

    @Operation(summary = "The workspace's trainings, the latest first")
    @GetMapping("/trainings")
    public List<TrainingDtos.Training> list() {
        return trainings.list(workspace());
    }

    @Operation(summary = "Record a training")
    @PostMapping("/trainings")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingDtos.Training create(@Valid @RequestBody TrainingDtos.Details details) {
        return trainings.create(workspace(), details);
    }

    @Operation(summary = "Change a training")
    @PutMapping("/trainings/{id}")
    public TrainingDtos.Training update(@PathVariable Long id, @Valid @RequestBody TrainingDtos.Details details) {
        return trainings.update(workspace(), id, details);
    }

    @Operation(summary = "Delete a training")
    @DeleteMapping("/trainings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        trainings.delete(workspace(), id);
    }

    private Long workspace() {
        return accessGuard.requireWorkspace(null);
    }
}
