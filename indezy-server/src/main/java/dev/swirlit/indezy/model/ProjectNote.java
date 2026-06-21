package dev.swirlit.indezy.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * A timestamped journal entry on a project (a call, an email, a decision, ...). Notes form a
 * chronological timeline per opportunity; ordering relies on {@link BaseEntity#getCreatedAt()}.
 */
@Entity
@Table(name = "project_notes")
@Getter
@Setter
public class ProjectNote extends BaseEntity {

    @NotBlank
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
}
