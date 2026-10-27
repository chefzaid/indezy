package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.PipelineTemplate;
import dev.swirlit.indezy.model.StepChecklistItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** A template written out step by step rather than saved from an opportunity, such as a starter process for anyone. */
public record PipelineTemplateStepsRequest(
    @NotBlank @Size(max = PipelineTemplate.NAME_MAX) String name,
    Long freelanceId,
    @NotEmpty @Size(max = PipelineTemplate.STEPS_MAX) List<@Valid Step> steps) {

    public record Step(
        @NotBlank @Size(max = PipelineTemplate.STEP_TITLE_MAX) String title,
        @Size(max = PipelineTemplate.CHECKLIST_MAX) List<@NotBlank @Size(max = StepChecklistItem.LABEL_MAX) String> checklist) {
    }
}
