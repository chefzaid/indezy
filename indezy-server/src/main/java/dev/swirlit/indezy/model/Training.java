package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.FundingStatus;
import dev.swirlit.indezy.model.enums.TrainingFund;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A training the freelancer followed or plans (see docs/features.md#training-record): its dates and cost, the
 * certification it leads to and when that expires, and the funding asked to the training fund of their status.
 */
@Entity
@Table(name = "trainings")
@Getter
@Setter
public class Training extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "provider", length = 200)
    private String provider;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "hours")
    private Integer hours;

    @Column(name = "cost", precision = 10, scale = 2)
    private BigDecimal cost;

    /** The certification obtained, shown on the public profile. */
    @Column(name = "certification", length = 200)
    private String certification;

    @Column(name = "certified_on")
    private LocalDate certifiedOn;

    @Column(name = "certification_expires_on")
    private LocalDate certificationExpiresOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "fund", length = 20)
    private TrainingFund fund;

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_status", length = 20)
    private FundingStatus fundingStatus;

    /** The last day the funding can be asked for, as the fund's rules of the year set it. */
    @Column(name = "funding_deadline")
    private LocalDate fundingDeadline;

    @Column(name = "funded_amount", precision = 10, scale = 2)
    private BigDecimal fundedAmount;

    @Column(name = "notes", length = 1000)
    private String notes;
}
