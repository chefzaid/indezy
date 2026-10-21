package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A night's median daily rate and open offers naming a skill first, in a region or in all of France: what the market
 * trends compare (see docs/features.md#market-trends). Kept 400 days.
 */
@Entity
@Table(name = "offer_rate_history",
    uniqueConstraints = @UniqueConstraint(name = "uk_offer_rate_history_day_skill_region", columnNames = {"computed_on", "skill_key", "region_code"}))
@Getter
@Setter
public class OfferRateHistory extends BaseEntity {

    /** The day it was computed, Paris time. */
    @Column(name = "computed_on", nullable = false)
    private LocalDate day;

    @Column(name = "skill_key", nullable = false, length = 60)
    private String skillKey;

    @Column(name = "skill", nullable = false, length = 60)
    private String skill;

    /** A region's code, or {@code OfferRateBenchmark#FRANCE}. */
    @Column(name = "region_code", nullable = false, length = 3)
    private String regionCode;

    @Column(name = "median_rate", nullable = false)
    private int medianRate;

    @Column(name = "offers", nullable = false)
    private int offers;
}
