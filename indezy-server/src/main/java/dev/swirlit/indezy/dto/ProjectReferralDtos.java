package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.ReferralBonusKind;
import dev.swirlit.indezy.model.enums.ReferralDirection;
import dev.swirlit.indezy.model.enums.ReferralStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Missions' co-optations and their bonuses, as the API reads and writes them. */
public final class ProjectReferralDtos {

    private ProjectReferralDtos() {
    }

    /** A co-optation to save: who, which way, the bonus (none, or its kind and amount), where it stands. */
    public record Referral(@NotBlank @Size(max = 120) String referrer, @NotNull ReferralDirection direction, ReferralBonusKind bonusKind,
                           @DecimalMin("0") @DecimalMax("1000000") BigDecimal bonusAmount, @NotNull ReferralStatus status, LocalDate paidOn,
                           @Size(max = 500) String note) {
    }

    /**
     * A mission's co-optation as read: what was saved, the days worked on the mission (its timesheets), what it earned
     * (those days at its daily rate), and the bonus that comes of it (none without a bonus).
     */
    public record View(Long projectId, String role, String clientName, Referral referral, double workedDays, BigDecimal earned, BigDecimal bonus) {
    }

    /** The account's co-optations, and the bonuses still to pay and to receive (not paid yet). */
    public record Summary(List<View> referrals, BigDecimal toPay, BigDecimal toReceive) {

        public Summary {
            referrals = List.copyOf(referrals);
        }
    }
}
