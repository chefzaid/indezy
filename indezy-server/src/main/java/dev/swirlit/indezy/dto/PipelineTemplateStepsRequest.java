package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.PipelineTemplate;
import dev.swirlit.indezy.model.StepChecklistItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A template written out step by step rather than saved from an opportunity, such as a starter process for anyone. */
public record PipelineTemplateStepsRequest(
    @NotBlank @Size(max = PipelineTemplate.NAME_MAX) String name,
    Long freelanceId,
    @NotEmpty @Size(max = PipelineTemplate.STEPS_MAX) List<@NotNull @Valid Step> steps) {

    // Copied without refusing nulls, so that validation, not the copy, answers a missing list or step.
    public PipelineTemplateStepsRequest {
        steps = steps == null ? null : Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public record Step(
        @NotBlank @Size(max = PipelineTemplate.STEP_TITLE_MAX) String title,
        @Size(max = PipelineTemplate.CHECKLIST_MAX) List<@NotBlank @Size(max = StepChecklistItem.LABEL_MAX) String> checklist) {

        public Step {
            checklist = checklist == null ? null : Collections.unmodifiableList(new ArrayList<>(checklist));
        }
    }
}
