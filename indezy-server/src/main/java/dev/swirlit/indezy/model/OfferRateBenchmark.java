package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * The median daily rate of the open offers naming a skill first, in a region or in all of France, as computed every
 * night (see docs/features.md#rate-versus-market).
 */
@Entity
@Table(name = "offer_rate_benchmarks",
    uniqueConstraints = @UniqueConstraint(name = "uk_offer_rate_benchmarks_skill_region", columnNames = {"skill_key", "region_code"}))
@Getter
@Setter
public class OfferRateBenchmark extends BaseEntity {

    /** The region code of all of France. */
    public static final String FRANCE = "FR";

    /** The skill's folded words. */
    @Column(name = "skill_key", nullable = false, length = 60)
    private String skillKey;

    /** The skill as the offers write it most. */
    @Column(name = "skill", nullable = false, length = 60)
    private String skill;

    /** A region's code, or {@link #FRANCE}. */
    @Column(name = "region_code", nullable = false, length = 3)
    private String regionCode;

    @Column(name = "median_rate", nullable = false)
    private int medianRate;

    /** The offers (one per mission) the median is taken over. */
    @Column(name = "offers", nullable = false)
    private int offers;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
}
