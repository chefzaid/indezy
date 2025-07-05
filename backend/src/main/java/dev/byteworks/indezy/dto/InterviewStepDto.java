package dev.byteworks.indezy.dto;

import dev.byteworks.indezy.model.enums.StepStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewStepDto {
    private Long id;

    @NotBlank(message = "Step title is required")
    private String title;

    private LocalDateTime date;

    @NotNull(message = "Step status is required")
    private StepStatus status;

    private String notes;

    // Related entities
    private Long projectId;
    private String projectRole;
}
