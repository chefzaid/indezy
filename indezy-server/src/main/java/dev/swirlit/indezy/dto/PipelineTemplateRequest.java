package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.PipelineTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Saves the steps of an opportunity as a template under a name, for a client or ESN or for anyone. */
public record PipelineTemplateRequest(
    @NotBlank @Size(max = PipelineTemplate.NAME_MAX) String name,
    @NotNull Long projectId,
    Long clientId) {
}
