package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One thing to prepare before an interview step (read the job description, test the video call...), ticked once done. */
@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class StepChecklistItem {

    @Column(name = "label", nullable = false, length = StepChecklistItem.LABEL_MAX)
    private String label;

    @Column(name = "done", nullable = false)
    private boolean done;

    public static final int LABEL_MAX = 200;
}
