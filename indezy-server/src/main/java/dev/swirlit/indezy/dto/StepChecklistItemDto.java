package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.StepChecklistItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One item of an interview step's preparation checklist. */
public record StepChecklistItemDto(
    @NotBlank @Size(max = StepChecklistItem.LABEL_MAX) String label,
    boolean done) {

    public StepChecklistItemDto {
        label = label == null ? null : label.strip();
    }
}
