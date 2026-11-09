package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.FundingStatus;
import dev.swirlit.indezy.model.enums.TrainingFund;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The training record's trainings, as shown and as written (see docs/features.md#training-record). */
public final class TrainingDtos {

    private TrainingDtos() {
    }

    /**
     * A training as shown, with what needs doing: {@code fundingDue} when its funding is still to ask and its deadline
     * is within 14 days or past, {@code certificationValidity} as compliance documents tell theirs.
     */
    public record Training(Long id, String title, String provider, LocalDate startsOn, LocalDate endsOn, Integer hours, BigDecimal cost,
                           String certification, LocalDate certifiedOn, LocalDate certificationExpiresOn,
                           ComplianceDtos.Validity certificationValidity, TrainingFund fund, FundingStatus fundingStatus,
                           LocalDate fundingDeadline, boolean fundingDue, BigDecimal fundedAmount, String notes) {
    }

    /** A training as written; only its title is required. */
    public record Details(@NotBlank @Size(max = 200) String title, @Size(max = 200) String provider, LocalDate startsOn, LocalDate endsOn,
                          @Min(0) @Max(10_000) Integer hours,
                          @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2) BigDecimal cost,
                          @Size(max = 200) String certification, LocalDate certifiedOn, LocalDate certificationExpiresOn,
                          TrainingFund fund, FundingStatus fundingStatus, LocalDate fundingDeadline,
                          @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2) BigDecimal fundedAmount,
                          @Size(max = 1000) String notes) {
    }
}
