package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * An application the daily autopilot run prepared overnight for an offer it found worth applying to (see
 * docs/features.md#daily-autopilot): the email to the ESN, the screening answers and the rate to propose, kept a week.
 */
@Entity
@Table(name = "autopilot_packs", indexes = @Index(name = "idx_autopilot_packs_freelance", columnList = "freelance_id"))
@Getter
@Setter
public class AutopilotPack extends BaseEntity {

    public static final int SUBJECT_MAX = 200;
    public static final int TEXT_MAX = 6_000;

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "offer_id", nullable = false)
    private Long offerId;

    @Column(name = "subject", length = SUBJECT_MAX)
    private String subject;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /** The screening questions and their answers, a question and its answer per line ("question — answer"). */
    @Column(name = "screening", columnDefinition = "TEXT")
    private String screening;

    /** The daily rate to propose, in euros; null when none was given. */
    @Column(name = "rate")
    private Integer rate;
}
