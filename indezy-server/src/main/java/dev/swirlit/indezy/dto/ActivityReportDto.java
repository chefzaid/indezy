package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A mission's activity report for one month with its calendar (public holidays, business days, mission dates) and
 * totals. A month without a saved report answers a draft without an id or days.
 */
@Data
@NoArgsConstructor
public class ActivityReportDto {
    private Long id;
    private Long projectId;
    private int year;
    private int month;
    private ActivityReportStatus status;
    private String comment;
    private List<ActivityDayDto> days;
    private List<PublicHolidayDto> publicHolidays;
    private int businessDays;
    private double workedDays;
    private double onsiteDays;
    private double remoteDays;
    private double absenceDays;
    /** The mission's agreed daily rate and the amount excluding VAT it gives for the worked days; null on public views. */
    private Integer dailyRate;
    private Double amount;
    private LocalDate missionStart;
    private LocalDate missionEnd;
    private Instant submittedAt;
    /** When the current validation link stops working; null when no link is active. */
    private Instant linkExpiresAt;
    @ToString.Exclude
    private String validatorName;
    private Instant decidedAt;
    @ToString.Exclude
    private String decisionComment;
    private LocalDateTime updatedAt;
}
