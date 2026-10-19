package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.OfferSite;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Collection schedule and health of one job board. A run claims the row (sets {@code runningSince})
 * with a conditional update, so two server replicas never read the same site at once.
 */
@Entity
@Table(name = "offer_site_statuses")
@Getter
@Setter
@NoArgsConstructor
public class OfferSiteStatus {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "site", length = 40)
    private OfferSite site;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "running_since")
    private Instant runningSince;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    /** Offers the last run read, freelance ones only. */
    @Column(name = "last_found")
    private Integer lastFound;

    /** Offers the last run saw for the first time. */
    @Column(name = "last_new")
    private Integer lastNew;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    public OfferSiteStatus(OfferSite site, Instant nextRunAt) {
        this.site = site;
        this.nextRunAt = nextRunAt;
    }
}
