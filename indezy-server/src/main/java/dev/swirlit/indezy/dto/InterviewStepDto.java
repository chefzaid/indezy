package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.StepStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class InterviewStepDto {
    private Long id;

    @NotBlank(message = "Step title is required")
    private String title;

    private LocalDateTime date;

    @NotNull(message = "Step status is required")
    private StepStatus status;

    private String notes;

    /** The preparation checklist; a step saved without one has none. */
    @Size(max = 30)
    private List<@Valid StepChecklistItemDto> checklist = new ArrayList<>();

    public void setChecklist(List<StepChecklistItemDto> checklist) {
        this.checklist = checklist == null ? new ArrayList<>() : checklist;
    }

    // Related entities
    private Long projectId;
    private String projectRole;
}
