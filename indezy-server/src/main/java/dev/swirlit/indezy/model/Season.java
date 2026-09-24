package dev.swirlit.indezy.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A job-hunting season: a bounded prospection period (like a sprint) with its own pipeline and
 * dashboard. Opportunities belong to at most one season; a season without an end date is still
 * running.
 */
@Entity
@Table(name = "seasons")
@Getter
@Setter
public class Season extends BaseEntity {

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Last day of the season; null while the season is still running. */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** Free-text objective, e.g. "Sign a remote Java mission before September". */
    @Column(name = "objective", columnDefinition = "TEXT")
    private String objective;

    /** Daily rate the freelance aims for during this season. */
    @Column(name = "target_daily_rate")
    private Integer targetDailyRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelance_id", nullable = false)
    private Freelance freelance;

    /** Whether the season covers the given day (open-ended seasons run until closed). */
    public boolean isActiveOn(final LocalDate day) {
        return startDate != null && !day.isBefore(startDate) && (endDate == null || !day.isAfter(endDate));
    }
}
