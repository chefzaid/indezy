package dev.swirlit.indezy.dto;

import java.util.List;

/** A saved recruitment process: its steps in order, and the client or ESN it is usual for (or none). */
public record PipelineTemplateDto(Long id, String name, Long clientId, String clientName, List<Step> steps) {

    public PipelineTemplateDto {
        steps = List.copyOf(steps);
    }

    /** One step of the process, with the preparation items it starts with. */
    public record Step(String title, List<String> checklist) {

        public Step {
            checklist = List.copyOf(checklist);
        }
    }
}
