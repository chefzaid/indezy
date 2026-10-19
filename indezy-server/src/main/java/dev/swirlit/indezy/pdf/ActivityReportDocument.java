package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.ActivityDay;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Everything an activity report PDF prints, read from the report, its mission and the client's template. */
public record ActivityReportDocument(
        ActivityReportTemplate template,
        String consultantName,
        String clientName,
        String middlemanName,
        String missionRole,
        YearMonth month,
        List<ActivityDay> days,
        String comment,
        ActivityReportStatus status,
        String validatorName,
        Instant decidedAt,
        String decisionComment) {

    public ActivityReportDocument {
        days = List.copyOf(days);
    }

    public Map<LocalDate, ActivityDay> daysByDate() {
        return days.stream().collect(Collectors.toMap(ActivityDay::getDate, Function.identity()));
    }

    public double workedDays() {
        return ActivityDay.workedDays(days);
    }

    public double daysOf(ActivityDayType type) {
        return ActivityDay.daysOf(days, type);
    }
}
