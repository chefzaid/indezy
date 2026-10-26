package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.StepChecklistItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One item of an interview step's preparation checklist. */
public record StepChecklistItemDto(
    @NotBlank @Size(max = StepChecklistItem.LABEL_MAX) String label,
    boolean done) {

    public StepChecklistItemDto {
        // One line: a pipeline template keeps a step's items one per line.
        label = label == null ? null : label.strip().replaceAll("\\s*\\R\\s*", " ");
    }
}
