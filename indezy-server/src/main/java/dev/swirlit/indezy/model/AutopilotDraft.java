package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A follow-up the daily autopilot run drafted overnight for a contact still waiting for an answer (see
 * docs/features.md#daily-autopilot), or the answer the inbox watcher drafted to an email the member forwarded: kept a
 * week for the member to copy and send, or dismiss.
 */
@Entity
@Table(name = "autopilot_drafts", indexes = @Index(name = "idx_autopilot_drafts_freelance", columnList = "freelance_id"))
@Getter
@Setter
public class AutopilotDraft extends BaseEntity {

    public static final int SUBJECT_MAX = 200;
    public static final int BODY_MAX = 6_000;

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "subject", length = SUBJECT_MAX)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    /** True for the answer to an email the member forwarded to the inbox watcher; null or false for a follow-up. */
    @Column(name = "answer")
    private Boolean answer;
}
