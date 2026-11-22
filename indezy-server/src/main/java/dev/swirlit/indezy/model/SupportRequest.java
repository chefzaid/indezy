package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A member's request to support (see docs/features.md#support-requests): its kind, subject, description and the
 * technical context they chose to send, then the administrators' answer. It goes with its account.
 */
@Entity
@Table(name = "support_requests", indexes = @Index(name = "idx_support_requests_user", columnList = "user_id"))
@Getter
@Setter
public class SupportRequest extends BaseEntity {

    public static final int SUBJECT_MAX = 150;
    public static final int DESCRIPTION_MAX = 5_000;
    public static final int CONTEXT_MAX = 2_000;
    public static final int ANSWER_MAX = 5_000;

    /** What the request is about. */
    public enum Kind { PROBLEM, FEATURE, QUESTION, ACCOUNT }

    /** Where the request stands. */
    public enum Status { OPEN, ANSWERED, CLOSED }

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private Kind kind;

    @Column(name = "subject", nullable = false, length = SUBJECT_MAX)
    private String subject;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /** The page, app version, browser, screen, language and error shown, as the member sent them; null when they did not. */
    @Column(name = "context", length = CONTEXT_MAX)
    private String context;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status = Status.OPEN;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "answered_at")
    private Instant answeredAt;

    // The request's text is the member's: kept out of logs.
    @Override
    public String toString() {
        return "SupportRequest[id=" + getId() + ", kind=" + kind + ", status=" + status + "]";
    }
}
