package dev.byteworks.indezy.dto;

import dev.byteworks.indezy.model.enums.WorkMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ProjectDto {
    private Long id;

    @NotBlank(message = "Role is required")
    private String role;

    private String description;
    private String techStack;

    @NotNull(message = "Daily rate is required")
    private Integer dailyRate;

    private WorkMode workMode;
    private Integer remoteDaysPerMonth;
    private Integer onsiteDaysPerMonth;
    private String advantages;
    private LocalDate startDate;
    private Integer durationInMonths;
    private Integer orderRenewalInMonths;
    private Integer daysPerYear;
    private List<String> documents;
    private String link;

    @Min(value = 1, message = "Personal rating must be between 1 and 5")
    @Max(value = 5, message = "Personal rating must be between 1 and 5")
    private Integer personalRating;

    private String notes;

    // Related entities
    private Long freelanceId;
    private Long clientId;
    private String clientName;
    private Long middlemanId;
    private String middlemanName;
    private Long sourceId;
    private String sourceName;

    // Computed fields
    private Integer totalRevenue;
    private Integer totalSteps;
    private Integer completedSteps;
    private Integer failedSteps;
}
