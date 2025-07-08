package dev.byteworks.indezy.dto;

import lombok.Data;

@Data
public class StepTransitionDto {
    private Long projectId;
    private String fromStepTitle;
    private String toStepTitle;
    private String notes;
}
