package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BenchPlanTest {

    // A Wednesday: the week started on Monday the 16th.
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    private static Project project(long id, ProjectStatus status, LocalDate start, Integer months, LocalDate created) {
        Project project = new Project();
        project.setId(id);
        project.setRole("Role " + id);
        project.setStatus(status);
        project.setStartDate(start);
        project.setDurationInMonths(months);
        project.setCreatedAt(created == null ? null : created.atTime(10, 0));
        return project;
    }

    @Test
    void setsAGoalForTheWeekFromTheMissionsEndAndThePipeline() {
        // The mission ends on 2027-01-04: 7 weeks away, so 5 a week, less 2 for the interview.
        Project mission = project(1, ProjectStatus.WON, LocalDate.of(2026, 7, 5), 6, LocalDate.of(2026, 6, 1));
        List<Project> projects = List.of(mission,
            project(2, ProjectStatus.CONTACT, null, null, LocalDate.of(2026, 11, 17)),
            project(3, ProjectStatus.INTERVIEW, null, null, LocalDate.of(2026, 11, 2)),
            project(4, ProjectStatus.LOST, null, null, LocalDate.of(2026, 10, 1)));
        DashboardStatsDto.BenchPlan plan = DashboardReminders.buildBenchPlan(projects, TODAY);

        assertThat(plan.getProjectId()).isEqualTo(1L);
        assertThat(plan.getEndDate()).isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(plan.getWeeksLeft()).isEqualTo(7);
        assertThat(plan.getActiveOpportunities()).isEqualTo(2);
        assertThat(plan.getAdvancedOpportunities()).isEqualTo(1);
        assertThat(plan.getAddedThisWeek()).isEqualTo(1);
        assertThat(plan.getDaysSinceLastAdded()).isEqualTo(1);
        assertThat(plan.getWeeklyGoal()).isEqualTo(3);
    }

    @Test
    void asksMostBetweenMissionsAndNothingWhileTheBenchIsFar() {
        DashboardStatsDto.BenchPlan bench = DashboardReminders.buildBenchPlan(List.of(
            project(1, ProjectStatus.WON, LocalDate.of(2025, 1, 1), 6, LocalDate.of(2025, 1, 1))), TODAY);
        assertThat(bench.getProjectId()).isNull();
        assertThat(bench.getWeeksLeft()).isZero();
        assertThat(bench.getWeeklyGoal()).isEqualTo(8);
        assertThat(bench.getDaysSinceLastAdded()).isNull();

        assertThat(DashboardReminders.buildBenchPlan(List.of(project(1, ProjectStatus.WON, LocalDate.of(2026, 11, 1), 6, null)), TODAY))
            .isNull();
        assertThat(DashboardReminders.buildBenchPlan(List.of(project(1, ProjectStatus.WON, LocalDate.of(2026, 1, 1), null, null)), TODAY))
            .isNull();

        // Two offers on the table still leave one to add.
        DashboardStatsDto.BenchPlan busy = DashboardReminders.buildBenchPlan(List.of(project(1, ProjectStatus.WON, LocalDate.of(2026, 10, 1), 2, null),
            project(2, ProjectStatus.OFFER, null, null, null), project(3, ProjectStatus.OFFER, null, null, null),
            project(4, ProjectStatus.INTERVIEW, null, null, null)), TODAY);
        assertThat(busy.getWeeksLeft()).isEqualTo(2);
        assertThat(busy.getWeeklyGoal()).isEqualTo(2);
    }
}
