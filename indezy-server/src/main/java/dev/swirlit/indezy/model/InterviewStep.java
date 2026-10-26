package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.StepStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "interview_steps")
@Getter
@Setter
public class InterviewStep extends BaseEntity {

    @NotBlank
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "date")
    private LocalDateTime date;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StepStatus status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** What to prepare before the step, in the order the freelancer listed it. */
    @ElementCollection
    @CollectionTable(name = "interview_step_checklist", joinColumns = @JoinColumn(name = "step_id"))
    @OrderColumn(name = "position")
    @BatchSize(size = 50)
    private List<StepChecklistItem> checklist = new ArrayList<>();

    // Helper methods
    public boolean isCompleted() {
        return StepStatus.VALIDATED.equals(status);
    }

    public boolean isFailed() {
        return StepStatus.FAILED.equals(status);
    }

    public boolean isPlanned() {
        return StepStatus.PLANNED.equals(status);
    }
}
