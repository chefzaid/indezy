package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.NetworkReportOutcome;
import dev.swirlit.indezy.model.enums.NetworkReportReason;
import dev.swirlit.indezy.model.enums.NetworkReportTarget;
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
 * A member's report of a post, a comment or a member, and what the moderators decided: kept once resolved as the
 * moderation's audit trail, with the reported text as it read, since a removed post or comment is gone.
 */
@Entity
@Table(name = "network_reports", indexes = {
    @Index(name = "idx_network_reports_target", columnList = "target_type, target_id"),
    @Index(name = "idx_network_reports_reporter", columnList = "reporter_id")})
@Getter
@Setter
public class NetworkReport extends BaseEntity {

    public static final int DETAILS_MAX = 500;
    public static final int NOTE_MAX = 500;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private NetworkReportTarget targetType;

    /** The post's or the comment's id, or the reported member's account id. */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** The account the sanction falls on: the author, or the member reported. */
    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    /** The post's or the comment's text when reported; none for a member. */
    @Column(name = "excerpt", length = NetworkPost.TEXT_MAX)
    private String excerpt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private NetworkReportReason reason;

    @Column(name = "details", length = DETAILS_MAX)
    private String details;

    /** Empty while open. */
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 20)
    private NetworkReportOutcome outcome;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** The moderator's note, for the audit trail; never shown to the members. */
    @Column(name = "note", length = NOTE_MAX)
    private String note;
}
