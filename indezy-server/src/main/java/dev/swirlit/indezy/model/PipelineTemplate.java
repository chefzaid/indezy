package dev.swirlit.indezy.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A recruitment process saved from an opportunity to start another one with: its steps in order, each with its
 * preparation checklist, and the client or ESN it is usual for, if any.
 */
@Entity
@Table(name = "pipeline_templates")
@Getter
@Setter
public class PipelineTemplate extends BaseEntity {

    public static final int NAME_MAX = 100;
    public static final int STEPS_MAX = 20;
    public static final int STEP_TITLE_MAX = 255;
    public static final int CHECKLIST_MAX = 20;

    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    /** The client or ESN whose process this is; null for a process used with anyone. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ElementCollection
    @CollectionTable(name = "pipeline_template_steps", joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "position")
    private List<TemplateStep> steps = new ArrayList<>();
}
