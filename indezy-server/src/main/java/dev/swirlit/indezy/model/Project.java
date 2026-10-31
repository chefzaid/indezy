package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.RefusedBy;
import dev.swirlit.indezy.model.enums.WorkMode;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter
@Setter
public class Project extends BaseEntity {

    /** Working days a year assumed for a mission that states none, as the yearly review does. */
    public static final int DEFAULT_DAYS_PER_YEAR = 218;

    /** The average year, to turn a period's calendar days into its share of a year. */
    private static final double DAYS_IN_A_YEAR = 365.25;


    /** The tags of the tech stack, comma-separated, fit in its column. */
    public static final int TECH_STACK_MAX = 255;

    @NotBlank
    @Column(name = "role", nullable = false)
    private String role;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProjectStatus status = ProjectStatus.CONTACT;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "tech_stack", length = TECH_STACK_MAX)
    private String techStack;

    @NotNull
    @Column(name = "daily_rate", nullable = false)
    private Integer dailyRate;

    /** Daily rate the end client pays the intermediary/ESN; the margin is the gap with {@link #dailyRate}. */
    @Column(name = "client_daily_rate")
    private Integer clientDailyRate;

    /** Rate first asked by the freelance during negotiation; {@link #dailyRate} holds the agreed rate. */
    @Column(name = "asked_daily_rate")
    private Integer askedDailyRate;

    /** Rate offered by the client/intermediary during negotiation; {@link #dailyRate} holds the agreed rate. */
    @Column(name = "offered_daily_rate")
    private Integer offeredDailyRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode")
    private WorkMode workMode;

    @Column(name = "remote_days_per_month")
    private Integer remoteDaysPerMonth;

    @Column(name = "onsite_days_per_month")
    private Integer onsiteDaysPerMonth;

    @Column(name = "advantages", columnDefinition = "TEXT")
    private String advantages;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "duration_in_months")
    private Integer durationInMonths;

    @Column(name = "order_renewal_in_months")
    private Integer orderRenewalInMonths;

    /** Months after the mission the ESN's non-solicitation clause forbids working for its end client without it. */
    @Column(name = "non_solicitation_months")
    private Integer nonSolicitationMonths;

    @Column(name = "days_per_year")
    private Integer daysPerYear;

    @ElementCollection
    @CollectionTable(name = "project_documents", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "document_path")
    private List<String> documents = new ArrayList<>();

    @Column(name = "link")
    private String link;

    @Min(1)
    @Max(5)
    @Column(name = "personal_rating")
    private Integer personalRating;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** Who refused the opportunity, the freelance or the recruiter; only meaningful when status is LOST. */
    @Enumerated(EnumType.STRING)
    @Column(name = "refused_by")
    private RefusedBy refusedBy;

    /** Why the opportunity was refused, when it was said; only meaningful when status is LOST. */
    @Enumerated(EnumType.STRING)
    @Column(name = "lost_reason")
    private LostReason lostReason;

    /** Pins hot leads to the top of their Kanban column and the favorites view. */
    @Column(name = "is_favorite", nullable = false, columnDefinition = "boolean default false")
    private Boolean isFavorite = false;

    /**
     * When the opportunity left the pipeline for the archive, by hand or after the inactivity period; its status is
     * kept, so restoring it puts it back in its Kanban column. Null while it is in the pipeline.
     */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    /** The day a won mission ended, set when it is ended from its page; null while it runs. */
    @Column(name = "ended_on")
    private LocalDate endedOn;

    /** Why the mission ended, as written when it was ended. */
    @Column(name = "end_reason", columnDefinition = "TEXT")
    private String endReason;

    /** Manual priority within a Kanban column (lower comes first); null keeps default ordering. */
    @Column(name = "board_position")
    private Integer boardPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "middleman_id")
    private Client middleman;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private Source source;

    /** Job-hunting season the opportunity belongs to; null for opportunities outside any season. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id")
    private Season season;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<InterviewStep> steps = new ArrayList<>();

    /** Coerce null to false so the non-nullable column is always populated. */
    public void setIsFavorite(final Boolean isFavorite) {
        this.isFavorite = isFavorite != null && isFavorite;
    }

    // Custom getters and setters for collections to prevent EI_EXPOSE_REP
    public List<String> getDocuments() {
        return documents != null ? new ArrayList<>(documents) : new ArrayList<>();
    }

    public void setDocuments(final List<String> documents) {
        this.documents = documents != null ? new ArrayList<>(documents) : new ArrayList<>();
    }

    public List<InterviewStep> getSteps() {
        return steps != null ? new ArrayList<>(steps) : new ArrayList<>();
    }

    public void setSteps(final List<InterviewStep> steps) {
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
    }

    // Helper methods

    /**
     * The mission's last day: the day it was ended, else its start plus its duration, else none (no start, or an
     * open-ended mission).
     */
    public LocalDate lastDay() {
        if (endedOn != null) {
            return endedOn;
        }
        return startDate != null && durationInMonths != null ? startDate.plusMonths(durationInMonths).minusDays(1) : null;
    }

    /**
     * Keeps what only one outcome says: who refused and why for a refused opportunity, the end of the mission for a won
     * one. Called once the status is set.
     */
    public void clearOtherOutcomes() {
        if (status != ProjectStatus.LOST) {
            refusedBy = null;
            lostReason = null;
        }
        if (status != ProjectStatus.WON) {
            endedOn = null;
            endReason = null;
        }
    }

    public Integer getTotalRevenue() {
        if (dailyRate != null && daysPerYear != null && durationInMonths != null) {
            return dailyRate * daysPerYear * durationInMonths / 12;
        }
        return null;
    }

    /**
     * What the mission is worth over its whole period: its daily rate times its working days, from its days per year
     * (218 when it states none), from its start to the day it ended when it was ended, else over its duration. Zero
     * without a daily rate or a period to count.
     */
    public double missionValue() {
        if (dailyRate == null) {
            return 0;
        }
        int perYear = daysPerYear != null ? daysPerYear : DEFAULT_DAYS_PER_YEAR;
        if (startDate != null && endedOn != null) {
            return (double) dailyRate * perYear * (ChronoUnit.DAYS.between(startDate, endedOn) + 1) / DAYS_IN_A_YEAR;
        }
        return durationInMonths != null ? (double) dailyRate * perYear * durationInMonths / 12 : 0;
    }

    /**
     * The part of {@link #missionValue()} still to come from {@code today}, pro rata of the days left: all of it before
     * the mission starts (or without a start date), none after its last day.
     */
    public double missionValueFrom(LocalDate today) {
        double value = missionValue();
        LocalDate last = lastDay();
        if (value == 0 || startDate == null || last == null || !today.isAfter(startDate)) {
            return value;
        }
        if (today.isAfter(last)) {
            return 0;
        }
        return value * (ChronoUnit.DAYS.between(today, last) + 1) / (ChronoUnit.DAYS.between(startDate, last) + 1);
    }

    /** Intermediary/ESN margin per day (client rate minus the freelance rate), or null when the client rate is unknown. */
    public Integer getMargin() {
        if (clientDailyRate != null && dailyRate != null) {
            return clientDailyRate - dailyRate;
        }
        return null;
    }

    /** Margin as a percentage of the client rate, rounded to the nearest point, or null when the client rate is missing or zero. */
    public Integer getMarginPercentage() {
        Integer margin = getMargin();
        if (margin != null && clientDailyRate != null && clientDailyRate != 0) {
            return Math.round(margin * 100f / clientDailyRate);
        }
        return null;
    }
}
