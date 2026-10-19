package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** The validator's answer through a validation link: approve, or reject with a reason. */
@Data
@NoArgsConstructor
public class ActivityReportDecisionRequest {

    @NotNull(message = "The decision is required")
    private Boolean approved;

    @ToString.Exclude
    @NotBlank(message = "Your name is required")
    @Size(max = 120, message = "Name must be at most 120 characters")
    private String validatorName;

    @ToString.Exclude
    @Size(max = 1000, message = "Comment must be at most 1000 characters")
    private String comment;
}
