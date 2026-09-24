package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SeasonDto {
    private Long id;

    @NotBlank(message = "Season name is required")
    private String name;

    @NotNull(message = "Season start date is required")
    private LocalDate startDate;

    private LocalDate endDate;
    private String objective;

    @Min(value = 1, message = "Target daily rate must be positive")
    private Integer targetDailyRate;

    private Long freelanceId;

    // Computed fields
    private Boolean active;
    private Integer projectCount;

    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
