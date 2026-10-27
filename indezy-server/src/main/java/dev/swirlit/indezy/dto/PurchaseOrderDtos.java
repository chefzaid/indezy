package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** A mission's purchase orders as shown, with what its activity reports used of them, and the request that writes one. */
public final class PurchaseOrderDtos {

    private PurchaseOrderDtos() {
    }

    /** How close the work came to the ceiling: under 80 %, from 80 %, or over it. */
    public enum Level { OK, NEAR, EXCEEDED }

    /**
     * An order or an amendment: its number, period, ceilings and notes. An amendment names the order it adds to
     * ({@code amendsId}) and has no start of its own.
     */
    public record Write(
        Long amendsId,
        @NotBlank @Size(max = 60) String number,
        LocalDate periodStart,
        LocalDate periodEnd,
        @DecimalMin("0.5") @DecimalMax("9999") @Digits(integer = 4, fraction = 2) BigDecimal dayCeiling,
        @DecimalMin("1") @DecimalMax("9999999") @Digits(integer = 7, fraction = 2) BigDecimal amountCeiling,
        @Size(max = 500) String notes) {
    }

    /** An amendment as listed under its order. */
    public record Amendment(Long id, String number, LocalDate periodEnd, BigDecimal dayCeiling, BigDecimal amountCeiling, String notes) {
    }

    /**
     * What the order allows with its amendments (days, euros, until when) and what the mission's activity reports used
     * of it over its period: the days worked, their amount at the mission's daily rate (empty without a rate), the
     * share of the closest ceiling, and the days left before it.
     */
    public record Usage(BigDecimal dayCeiling, BigDecimal amountCeiling, LocalDate periodEnd, BigDecimal workedDays, BigDecimal amount,
                        BigDecimal share, BigDecimal daysLeft, Level level) {
    }

    public record Order(Long id, Long projectId, String number, LocalDate periodStart, LocalDate periodEnd, BigDecimal dayCeiling,
                        BigDecimal amountCeiling, String notes, List<Amendment> amendments, Usage usage) {
        public Order {
            amendments = List.copyOf(amendments);
        }
    }
}
