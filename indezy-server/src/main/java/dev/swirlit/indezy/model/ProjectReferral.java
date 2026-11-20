package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.ReferralBonusKind;
import dev.swirlit.indezy.model.enums.ReferralDirection;
import dev.swirlit.indezy.model.enums.ReferralStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A mission's co-optation: who referred it, and the bonus paid for it (see docs/features.md#co-optation). */
@Entity
@Table(name = "project_referrals")
@Getter
@Setter
public class ProjectReferral extends BaseEntity {

    public static final int REFERRER_MAX = 120;
    public static final int NOTE_MAX = 500;

    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    /** Who referred the mission, or whom the freelancer referred: a name, or a member's @handle. */
    @Column(name = "referrer", nullable = false, length = REFERRER_MAX)
    private String referrer;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private ReferralDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "bonus_kind", length = 10)
    private ReferralBonusKind bonusKind;

    /** Euros for a fixed bonus or a bonus per day, a percentage for a share; none without a bonus. */
    @Column(name = "bonus_amount", precision = 10, scale = 2)
    private BigDecimal bonusAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ReferralStatus status;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "note", length = NOTE_MAX)
    private String note;
}
