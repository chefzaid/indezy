package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.StepStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardTrendsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    @Test
    void comparesTheLastNinetyDaysWithTheNinetyBefore() {
        // Three opportunities added lately (one today, one on the window's first day), two before, one too old to count.
        Project signed = project(1L, TODAY.minusDays(10), ProjectStatus.WON, 700, TODAY.plusDays(20));
        Project started = project(2L, TODAY.minusDays(89), ProjectStatus.WON, 600, TODAY.minusDays(5));
        Project before = project(3L, TODAY.minusDays(90), ProjectStatus.WON, 500, TODAY.minusDays(100));
        Project lost = project(4L, TODAY.minusDays(179), ProjectStatus.LOST, null, null);
        Project old = project(5L, TODAY.minusDays(180), ProjectStatus.WON, null, null);
        Project today = project(6L, TODAY, ProjectStatus.CONTACT, 800, null);
        List<InterviewStep> steps = List.of(step(TODAY.minusDays(1), StepStatus.VALIDATED), step(TODAY.minusDays(2), StepStatus.CANCELED),
            step(TODAY.minusDays(95), StepStatus.FAILED), step(TODAY.minusDays(96), StepStatus.PLANNED), step(TODAY.minusDays(97), null));
        Map<Long, LocalDateTime> signatures = Map.of(1L, TODAY.minusDays(3).atTime(15, 0));

        List<DashboardStatsDto.Trend> trends = DashboardTrends.build(List.of(signed, started, before, lost, old, today), steps, signatures,
            TODAY);

        assertThat(trends).extracting(DashboardStatsDto.Trend::getMetric)
            .containsExactly("OPPORTUNITIES", "INTERVIEWS", "WON", "OBTAINED_RATE");
        assertThat(trends.get(0)).isEqualTo(new DashboardStatsDto.Trend("OPPORTUNITIES", 3.0, 2.0, 50));
        assertThat(trends.get(1)).isEqualTo(new DashboardStatsDto.Trend("INTERVIEWS", 1.0, 3.0, -67));
        // Won by its signature, or by its start without one.
        assertThat(trends.get(2)).isEqualTo(new DashboardStatsDto.Trend("WON", 2.0, 1.0, 100));
        assertThat(trends.get(3)).isEqualTo(new DashboardStatsDto.Trend("OBTAINED_RATE", 650.0, 500.0, 30));
    }

    @Test
    void comparesNothingWithAnEmptyPeriod() {
        List<DashboardStatsDto.Trend> trends = DashboardTrends.build(List.of(project(1L, TODAY, ProjectStatus.CONTACT, null, null)),
            List.of(), Map.of(), TODAY);

        assertThat(trends.get(0)).isEqualTo(new DashboardStatsDto.Trend("OPPORTUNITIES", 1.0, 0.0, null));
        assertThat(trends.get(3)).isEqualTo(new DashboardStatsDto.Trend("OBTAINED_RATE", null, null, null));
    }

    private static Project project(Long id, LocalDate added, ProjectStatus status, Integer rate, LocalDate start) {
        Project project = new Project();
        project.setId(id);
        project.setCreatedAt(added.atTime(9, 0));
        project.setStatus(status);
        project.setDailyRate(rate);
        project.setStartDate(start);
        return project;
    }

    private static InterviewStep step(LocalDate date, StepStatus status) {
        InterviewStep step = new InterviewStep();
        step.setDate(date.atTime(10, 0));
        step.setStatus(status);
        return step;
    }
}
