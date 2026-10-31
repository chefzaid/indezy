package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.RefusedBy;
import dev.swirlit.indezy.model.enums.WorkMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectDto {
    private Long id;

    @NotBlank(message = "Role is required")
    private String role;

    private ProjectStatus status;
    private String description;
    @Size(max = Project.TECH_STACK_MAX)
    private String techStack;

    @NotNull(message = "Daily rate is required")
    private Integer dailyRate;

    private Integer clientDailyRate;
    private Integer askedDailyRate;
    private Integer offeredDailyRate;

    private WorkMode workMode;
    private Integer remoteDaysPerMonth;
    private Integer onsiteDaysPerMonth;
    private String advantages;
    private LocalDate startDate;
    private Integer durationInMonths;
    private Integer orderRenewalInMonths;
    /** Months the ESN's non-solicitation clause runs after the mission. */
    @Min(1)
    @Max(36)
    private Integer nonSolicitationMonths;
    private Integer daysPerYear;
    private List<String> documents;
    private String link;

    @Min(value = 1, message = "Personal rating must be between 1 and 5")
    @Max(value = 5, message = "Personal rating must be between 1 and 5")
    private Integer personalRating;

    private String notes;

    private RefusedBy refusedBy;

    private LostReason lostReason;

    private Boolean isFavorite;

    /** When the opportunity was archived, or null in the pipeline; changed only by archiving and restoring it. */
    private LocalDateTime archivedAt;

    /** The day the won mission ended, and why; changed only by ending and resuming it. */
    private LocalDate endedOn;
    private String endReason;

    // Related entities
    private Long freelanceId;
    private Long clientId;
    private String clientName;
    private Long middlemanId;
    private String middlemanName;
    private Long sourceId;
    private String sourceName;
    private Long seasonId;
    private String seasonName;

    // Computed fields
    private Integer totalRevenue;
    private Integer margin;
    private Integer marginPercentage;
    private Integer totalSteps;
    private Integer completedSteps;
    private Integer failedSteps;

    // Interview steps (only populated when explicitly requested)
    private List<InterviewStepDto> steps;

    // Custom getters and setters for collections to prevent EI_EXPOSE_REP
    public List<String> getDocuments() {
        return documents != null ? new ArrayList<>(documents) : new ArrayList<>();
    }

    public void setDocuments(final List<String> documents) {
        this.documents = documents != null ? new ArrayList<>(documents) : new ArrayList<>();
    }

    public List<InterviewStepDto> getSteps() {
        return steps != null ? new ArrayList<>(steps) : new ArrayList<>();
    }

    public void setSteps(final List<InterviewStepDto> steps) {
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
    }
}
