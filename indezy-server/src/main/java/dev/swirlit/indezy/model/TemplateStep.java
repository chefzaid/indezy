package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

/**
 * One step of a pipeline template: its title and the preparation items it starts with, kept one per line since an
 * embedded collection cannot hold another one (an item is a single line of the step's checklist).
 */
@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class TemplateStep {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "checklist", columnDefinition = "TEXT")
    private String checklist;

    public TemplateStep(String title, List<String> checklist) {
        this.title = title;
        this.checklist = checklist.isEmpty() ? null : String.join("\n", checklist);
    }

    /** The preparation items, in their order. */
    public List<String> checklistItems() {
        return checklist == null || checklist.isBlank() ? List.of() : Arrays.stream(checklist.split("\n")).toList();
    }
}
