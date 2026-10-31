package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.ActivityDay;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.repository.ActivityReportRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BilledDaysServiceTest {

    @Mock
    ActivityReportRepository reports;

    @Mock
    FreelanceRepository freelances;

    @InjectMocks
    BilledDaysService service;

    private static ActivityReport report(int year, int month, ActivityReportStatus status, int fullDays, boolean halfDay) {
        ActivityReport report = new ActivityReport();
        report.setReportYear(year);
        report.setReportMonth(month);
        report.setStatus(status);
        List<ActivityDay> days = new ArrayList<>();
        for (int day = 1; day <= fullDays; day++) {
            days.add(new ActivityDay(LocalDate.of(year, month, day), ActivityDayType.REMOTE, false));
        }
        if (halfDay) {
            days.add(new ActivityDay(LocalDate.of(year, month, 28), ActivityDayType.ONSITE, true));
        }
        days.add(new ActivityDay(LocalDate.of(year, month, 27), ActivityDayType.ABSENCE, false));
        report.setDays(days);
        return report;
    }

    @Test
    void countsThisYearsValidatedDaysAndProjectsTheCompletedMonths() {
        when(reports.findByProjectFreelanceIdOrderByReportYearDescReportMonthDesc(1L)).thenReturn(List.of(
            report(2026, 4, ActivityReportStatus.VALIDATED, 4, false),
            report(2026, 3, ActivityReportStatus.SUBMITTED, 20, false),
            report(2026, 2, ActivityReportStatus.VALIDATED, 18, true),
            report(2026, 1, ActivityReportStatus.VALIDATED, 20, false),
            report(2025, 12, ActivityReportStatus.VALIDATED, 15, false)));
        when(freelances.findBilledDaysTarget(1L)).thenReturn(Optional.of(200));

        DashboardStatsDto.BilledDays days = service.ofYear(1L, LocalDate.of(2026, 4, 10));

        // April's report counts as billed but not in the pace: the month is not over.
        assertThat(days).isEqualTo(new DashboardStatsDto.BilledDays(2026, 42.5, 200, 154));
    }

    @Test
    void hasNoProjectionInJanuaryNorTargetWhenNoneIsSet() {
        when(reports.findByProjectFreelanceIdOrderByReportYearDescReportMonthDesc(1L)).thenReturn(List.of(
            report(2026, 1, ActivityReportStatus.VALIDATED, 5, false)));
        when(freelances.findBilledDaysTarget(1L)).thenReturn(Optional.empty());

        assertThat(service.ofYear(1L, LocalDate.of(2026, 1, 20)))
            .isEqualTo(new DashboardStatsDto.BilledDays(2026, 5, null, null));
    }
}
