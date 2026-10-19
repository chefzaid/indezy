package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.ActivityDayType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** One recorded day of an activity report; days without an entry were not worked. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDayDto {

    @NotNull(message = "Day date is required")
    private LocalDate date;

    @NotNull(message = "Day type is required")
    private ActivityDayType type;

    /** True for a half day; a missing value is a full day. */
    private Boolean halfDay;

    /** Whether the entry covers half a day, without a bean-style name so JSON keeps only {@code halfDay}. */
    public boolean countsAsHalfDay() {
        return Boolean.TRUE.equals(halfDay);
    }
}
