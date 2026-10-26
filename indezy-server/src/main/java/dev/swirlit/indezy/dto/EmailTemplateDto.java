package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.EmailTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A saved message with its variables left as written ({@code {{firstName}}}, {@code {{role}}}...). */
public record EmailTemplateDto(
    Long id,
    @NotBlank @Size(max = EmailTemplate.NAME_MAX) String name,
    @NotBlank @Size(max = EmailTemplate.SUBJECT_MAX) String subject,
    @NotBlank @Size(max = EmailTemplate.BODY_MAX) String body) {
}
