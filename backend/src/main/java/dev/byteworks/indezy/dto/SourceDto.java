package dev.byteworks.indezy.dto;

import dev.byteworks.indezy.model.enums.SourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SourceDto {
    private Long id;

    @NotBlank(message = "Source name is required")
    private String name;

    @NotNull(message = "Source type is required")
    private SourceType type;

    private String link;
    private Boolean isListing = false;

    @Min(value = 1, message = "Popularity rating must be between 1 and 5")
    @Max(value = 5, message = "Popularity rating must be between 1 and 5")
    private Integer popularityRating;

    @Min(value = 1, message = "Usefulness rating must be between 1 and 5")
    @Max(value = 5, message = "Usefulness rating must be between 1 and 5")
    private Integer usefulnessRating;

    private String notes;

    // Related entities
    @NotNull(message = "Freelance ID is required")
    private Long freelanceId;

    // Computed fields
    private Integer totalProjects;
    private Double averageDailyRate;

    // Related collections (for detailed views)
    private java.util.List<dev.byteworks.indezy.dto.ProjectDto> projects;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
