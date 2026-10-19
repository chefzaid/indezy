package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.ActivityDayType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Collection;

/** One recorded day of an activity report: worked on site or remotely, or absent, for a full or a half day. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ActivityDay {

    @Column(name = "day_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false)
    private ActivityDayType type;

    @Column(name = "half_day", nullable = false)
    private boolean halfDay;

    /** The share of the day the entry covers: 1 for a full day, 0.5 for a half day. */
    public double getDuration() {
        return halfDay ? 0.5 : 1;
    }

    /** The worked share of the day, 0 for an absence. */
    public double getWorkedDuration() {
        return type.isWorked() ? getDuration() : 0;
    }

    /** Days worked, on site and remotely, half days counting for 0.5. */
    public static double workedDays(Collection<ActivityDay> days) {
        return days.stream().mapToDouble(ActivityDay::getWorkedDuration).sum();
    }

    /** Days of one type (on site, remote or absent), half days counting for 0.5. */
    public static double daysOf(Collection<ActivityDay> days, ActivityDayType type) {
        return days.stream().filter(day -> day.type == type).mapToDouble(ActivityDay::getDuration).sum();
    }
}
