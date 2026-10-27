package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.YearlyReviewDto;
import dev.swirlit.indezy.dto.YearlyReviewDto.Mission;
import dev.swirlit.indezy.model.ActivityDay;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class YearlyReviewServiceTest {

    @Test
    void estimatesTheDaysOfTheMissionsWonThatTheYearCovers() {
        Project spring = mission(1L, "Java", LocalDate.of(2025, 9, 1), 6, 600, 218);
        Project summer = mission(2L, "Kafka", LocalDate.of(2026, 7, 1), null, 700, null);
        Project before = mission(3L, "Old", LocalDate.of(2024, 1, 1), 12, 500, 218);
        Project lost = mission(4L, "Lost", LocalDate.of(2026, 1, 1), 6, 900, 218);
        lost.setStatus(ProjectStatus.LOST);

        List<Mission> missions = YearlyReviewService.missions(List.of(summer, before, lost, spring), List.of(), 2026);

        assertThat(missions).extracting(Mission::role, Mission::end, Mission::days, Mission::reported, Mission::revenue)
            .containsExactly(
                // 1 January to 28 February: 59 days of 365, of 218 working days a year.
                tuple("Java", LocalDate.of(2026, 2, 28), 35.0, false, 21_000L),
                // Open-ended from 1 July: 184 days, at the 218 days a year assumed when none is stated.
                tuple("Kafka", null, 110.0, false, 77_000L));
    }

    @Test
    void takesTheDaysFromTheActivityReportsWhenThereAreSome() {
        Project kafka = mission(2L, "Kafka", LocalDate.of(2026, 7, 1), null, 700, null);
        ActivityReport july = report(kafka, 2026, 7, 15);
        ActivityReport august = report(kafka, 2026, 8, 3);
        ActivityReport lastYear = report(kafka, 2025, 12, 20);

        List<Mission> missions = YearlyReviewService.missions(List.of(kafka), List.of(july, august, lastYear), 2026);

        assertThat(missions).extracting(Mission::days, Mission::reported, Mission::revenue).containsExactly(tuple(18.0, true, 12_600L));
    }

    @Test
    void addsUpTheYearAndItsAverageRateOverThePaidDays() {
        YearlyReviewDto.Totals totals = YearlyReviewService.totals(List.of(
            new Mission(1L, "A", null, 600, LocalDate.of(2026, 1, 1), null, 10, true, 6_000L),
            new Mission(2L, "B", null, 800, LocalDate.of(2026, 1, 1), null, 30, true, 24_000L),
            new Mission(3L, "C", null, null, LocalDate.of(2026, 1, 1), null, 5, false, null)));

        assertThat(totals).isEqualTo(new YearlyReviewDto.Totals(30_000, 45, 750, 3));
        assertThat(YearlyReviewService.totals(List.of()).averageDailyRate()).isNull();
    }

    @Test
    void countsTheOpportunitiesOpenedHowTheyEndedAndWhyTheyWereLost() {
        Project won = mission(1L, "A", LocalDate.of(2026, 1, 1), 6, 600, null);
        Project rate = lostFor(LostReason.RATE_TOO_LOW);
        Project rateAgain = lostFor(LostReason.RATE_TOO_LOW);
        Project filled = lostFor(LostReason.POSITION_FILLED);
        Project open = new Project();
        open.setStatus(ProjectStatus.INTERVIEW);

        YearlyReviewDto.Pipeline pipeline = YearlyReviewService.pipeline(List.of(won, rate, rateAgain, filled, open));

        assertThat(pipeline.opened()).isEqualTo(5);
        assertThat(pipeline.won()).isEqualTo(1);
        assertThat(pipeline.lost()).isEqualTo(3);
        assertThat(pipeline.conversionRate()).isEqualTo(25);
        assertThat(pipeline.lostReasons()).extracting(YearlyReviewDto.ReasonCount::reason, YearlyReviewDto.ReasonCount::count)
            .containsExactly(tuple(LostReason.RATE_TOO_LOW, 2), tuple(LostReason.POSITION_FILLED, 1));
        assertThat(YearlyReviewService.pipeline(List.of(open)).conversionRate()).isNull();
    }

    private static Project mission(Long id, String role, LocalDate start, Integer months, Integer rate, Integer daysPerYear) {
        Project project = new Project();
        project.setId(id);
        project.setRole(role);
        project.setStatus(ProjectStatus.WON);
        project.setStartDate(start);
        project.setDurationInMonths(months);
        project.setDailyRate(rate);
        project.setDaysPerYear(daysPerYear);
        Client client = new Client();
        client.setCompanyName("Acme");
        project.setClient(client);
        return project;
    }

    private static Project lostFor(LostReason reason) {
        Project project = new Project();
        project.setStatus(ProjectStatus.LOST);
        project.setLostReason(reason);
        return project;
    }

    private static ActivityReport report(Project project, int year, int month, int workedDays) {
        ActivityReport report = new ActivityReport();
        report.setProject(project);
        report.setReportYear(year);
        report.setReportMonth(month);
        report.setDays(java.util.stream.IntStream.rangeClosed(1, workedDays)
            .mapToObj(day -> new ActivityDay(LocalDate.of(year, month, day), ActivityDayType.ONSITE, false)).toList());
        return report;
    }
}
