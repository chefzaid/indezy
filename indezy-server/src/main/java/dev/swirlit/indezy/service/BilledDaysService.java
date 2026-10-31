package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.ActivityDay;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.repository.ActivityReportRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * This year's billed days (see docs/features.md#dashboard-and-analytics): the days of the activity reports the clients
 * validated, against the yearly target set on the profile, and the year at the pace of its completed months.
 */
@Service
@RequiredArgsConstructor
public class BilledDaysService {

    private static final int MONTHS = 12;

    private final ActivityReportRepository reports;
    private final FreelanceRepository freelances;

    @Transactional(readOnly = true)
    public DashboardStatsDto.BilledDays ofYear(Long freelanceId, LocalDate today) {
        List<ActivityReport> validated = reports.findByProjectFreelanceIdOrderByReportYearDescReportMonthDesc(freelanceId).stream()
            .filter(report -> report.getReportYear() == today.getYear() && report.getStatus() == ActivityReportStatus.VALIDATED)
            .toList();
        double billed = validated.stream().mapToDouble(report -> ActivityDay.workedDays(report.getDays())).sum();
        int completedMonths = today.getMonthValue() - 1;
        double completed = validated.stream().filter(report -> report.getReportMonth() <= completedMonths)
            .mapToDouble(report -> ActivityDay.workedDays(report.getDays())).sum();
        Integer projected = completedMonths == 0 ? null : (int) Math.round(completed / completedMonths * MONTHS);
        return new DashboardStatsDto.BilledDays(today.getYear(), billed, freelances.findBilledDaysTarget(freelanceId).orElse(null), projected);
    }
}
