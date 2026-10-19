package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Monthly activity report (CRA, compte rendu d'activité) of a mission: the days worked on site or remotely and the
 * absences of one month, validated by the client or the ESN through a link sent by the freelance.
 */
@Entity
@Table(name = "activity_reports", uniqueConstraints = @UniqueConstraint(
    name = "uk_activity_reports_project_month", columnNames = {"project_id", "report_year", "report_month"}))
@Getter
@Setter
public class ActivityReport extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "report_year", nullable = false)
    private int reportYear;

    @Column(name = "report_month", nullable = false)
    private int reportMonth;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ActivityReportStatus status = ActivityReportStatus.DRAFT;

    /** The freelance's note to the validator. */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @ElementCollection
    @CollectionTable(name = "activity_report_days", joinColumns = @JoinColumn(name = "report_id"))
    @OrderBy("date")
    private List<ActivityDay> days = new ArrayList<>();

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** SHA-256 of the validation link token, hex encoded; the token itself is never stored. */
    @Column(name = "validation_token_hash", length = 64, unique = true)
    private String validationTokenHash;

    @Column(name = "validation_expires_at")
    private Instant validationExpiresAt;

    /** Name the client or ESN signed with when validating or rejecting. */
    @Column(name = "validator_name", length = 120)
    private String validatorName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** The validator's comment, or the reason of a rejection. */
    @Column(name = "decision_comment", columnDefinition = "TEXT")
    private String decisionComment;

    public List<ActivityDay> getDays() {
        return days != null ? new ArrayList<>(days) : new ArrayList<>();
    }

    public void setDays(final List<ActivityDay> days) {
        this.days = days != null ? new ArrayList<>(days) : new ArrayList<>();
    }

    public YearMonth getPeriod() {
        return YearMonth.of(reportYear, reportMonth);
    }

    public double getWorkedDays() {
        return ActivityDay.workedDays(days);
    }

    public double getDaysOf(ActivityDayType type) {
        return ActivityDay.daysOf(days, type);
    }

    /** Forgets the validation link and the validator's decision, as a reopened or resubmitted report does. */
    public void clearValidation() {
        validationTokenHash = null;
        validationExpiresAt = null;
        validatorName = null;
        decidedAt = null;
        decisionComment = null;
    }
}
