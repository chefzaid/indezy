package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Season;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.WorkMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DashboardStatsServiceTest extends DashboardStatsTestSupport {

    @Test
    void getDashboardStats_ShouldCompareTheProfileTargetWithOnlyAcceptedRates() {
        Project won = projectWithStatus(ProjectStatus.WON);
        won.setDailyRate(600);
        Project anotherWon = projectWithStatus(ProjectStatus.WON);
        anotherWon.setDailyRate(650);
        Project unknownRate = projectWithStatus(ProjectStatus.WON);
        testProject.setStatus(ProjectStatus.OFFER);
        testProject.setDailyRate(900);
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(won, anotherWon, unknownRate, testProject));
        when(freelanceRepository.findTargetDailyRate(1L)).thenReturn(Optional.of(700));

        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        assertThat(stats.getTargetDailyRate()).isEqualTo(700);
        assertThat(stats.getAverageObtainedDailyRate()).isEqualTo(625);
    }

    @Test
    void getDashboardStats_ShouldCountExactRatesInNumericOrder() {
        List<Project> projects = java.util.stream.Stream.of(650, 600, 1000, 550, 600, 0, null, -1)
            .map(rate -> {
                Project project = new Project();
                project.setDailyRate(rate);
                return project;
            }).toList();
        when(projectRepository.findByFreelanceId(1L)).thenReturn(projects);

        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        assertThat(stats.getDailyRateRanges())
            .extracting(DashboardStatsDto.DailyRateRange::getLabel, DashboardStatsDto.DailyRateRange::getCount)
            .containsExactly(tuple("0", 1L), tuple("550", 1L), tuple("600", 2L), tuple("650", 1L), tuple("1000", 1L));
    }

    @Test
    void getDashboardStats_ShouldOmitUnknownRates() {
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(new Project()));

        assertThat(dashboardStatsService.getDashboardStats(1L, null).getDailyRateRanges()).isEmpty();
    }

    @Test
    void getDashboardStats_ShouldAggregateCountsRatesAndRanges() {
        // Given
        testProject.setStatus(ProjectStatus.WON);
        Project contact = new Project();
        contact.setStatus(ProjectStatus.CONTACT);
        contact.setWorkMode(WorkMode.HYBRID);
        contact.setDailyRate(600);
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject, contact));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Then
        assertThat(stats.getTotalProjects()).isEqualTo(2L);
        assertThat(stats.getAverageDailyRate()).isEqualTo(600.0);
        assertThat(stats.getWonProjects()).isEqualTo(1L);
        assertThat(stats.getLostProjects()).isZero();
        assertThat(stats.getActiveProjects()).isEqualTo(1L);
        assertThat(stats.getProjectsByStatus().get("WON")).isEqualTo(1L);
        assertThat(stats.getProjectsByWorkMode().get("HYBRID")).isEqualTo(2L);
        // Both opportunities negotiated the same exact daily rate.
        assertThat(stats.getDailyRateRanges())
                .singleElement()
                .satisfies(r -> assertThat(r.getLabel()).isEqualTo("600"))
                .satisfies(r -> assertThat(r.getCount()).isEqualTo(2L));
    }

    @Test
    void getDashboardStats_ShouldBreakDownLostReasons() {
        // Given a lost project with a reason and a non-lost project
        Project lost = new Project();
        lost.setStatus(ProjectStatus.LOST);
        lost.setLostReason(LostReason.RATE_TOO_LOW);
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject, lost));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Then only the lost project contributes to the breakdown
        assertThat(stats.getLostReasonsBreakdown())
                .containsEntry("RATE_TOO_LOW", 1L)
                .containsEntry("NO_RESPONSE", 0L);
    }

    @Test
    void getDashboardStats_ShouldComputeForecastRevenueWeightedByStatus() {
        // Given a signed contract (120,000 at 100%) and an interview-stage one (66,000 at 50%).
        Project won = new Project();
        won.setStatus(ProjectStatus.WON);
        won.setDailyRate(500);
        won.setDaysPerYear(240);
        won.setDurationInMonths(12);
        Project interview = new Project();
        interview.setStatus(ProjectStatus.INTERVIEW);
        interview.setDailyRate(600);
        interview.setDaysPerYear(220);
        interview.setDurationInMonths(6);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(won, interview));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Then: 120,000 * 1.0 + 66,000 * 0.5 = 153,000, while total revenue stays unweighted.
        assertThat(stats.getForecastRevenue()).isEqualTo(153000.0);
        assertThat(stats.getTotalEstimatedRevenue()).isEqualTo(186000.0);
    }

    @Test
    void getDashboardStats_ShouldLeaveLostOpportunitiesOutOfEstimatedRevenue() {
        // Given a signed contract (120,000) and a lost opportunity that would have been worth 66,000.
        Project won = new Project();
        won.setStatus(ProjectStatus.WON);
        won.setDailyRate(500);
        won.setDaysPerYear(240);
        won.setDurationInMonths(12);
        Project lost = new Project();
        lost.setStatus(ProjectStatus.LOST);
        lost.setDailyRate(600);
        lost.setDaysPerYear(220);
        lost.setDurationInMonths(6);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(won, lost));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Then only the signed contract counts.
        assertThat(stats.getTotalEstimatedRevenue()).isEqualTo(120000.0);
        assertThat(stats.getForecastRevenue()).isEqualTo(120000.0);
    }

    @Test
    void getDashboardStats_ShouldBuildPipelineConversionFunnel() {
        // Given 6 live opportunities spread across stages and 1 lost (excluded).
        List<Project> projects = List.of(
            projectWithStatus(ProjectStatus.CONTACT),
            projectWithStatus(ProjectStatus.CONTACT),
            projectWithStatus(ProjectStatus.CONTACT),
            projectWithStatus(ProjectStatus.INTERVIEW),
            projectWithStatus(ProjectStatus.OFFER),
            projectWithStatus(ProjectStatus.WON),
            projectWithStatus(ProjectStatus.LOST)
        );
        when(projectRepository.findByFreelanceId(1L)).thenReturn(projects);

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Then each stage counts opportunities at or beyond it; lost ones are not counted.
        List<DashboardStatsDto.ConversionFunnelStage> funnel = stats.getConversionFunnel();
        assertThat(funnel).hasSize(4);
        assertThat(funnel).extracting(DashboardStatsDto.ConversionFunnelStage::getStage)
            .containsExactly("CONTACT", "INTERVIEW", "OFFER", "WON");
        assertThat(funnel).extracting(DashboardStatsDto.ConversionFunnelStage::getCount)
            .containsExactly(6L, 3L, 2L, 1L);
        assertThat(funnel).extracting(DashboardStatsDto.ConversionFunnelStage::getConversionRate)
            .containsExactly(100.0, 50.0, 33.3, 16.7);
    }

    @Test
    void getDashboardStats_WithSeason_ShouldOnlyCountThatSeasonsOpportunitiesAndSteps() {
        Season autumn = new Season();
        autumn.setId(9L);
        Project won = projectWithStatus(ProjectStatus.WON);
        won.setSeason(autumn);
        won.setDailyRate(700);
        Project contact = projectWithStatus(ProjectStatus.CONTACT);
        contact.setSeason(autumn);
        contact.setDailyRate(500);
        Project otherSeason = projectWithStatus(ProjectStatus.LOST);

        InterviewStep inSeason = new InterviewStep();
        inSeason.setProject(won);
        inSeason.setDate(LocalDateTime.now().minusDays(2));
        InterviewStep outOfSeason = new InterviewStep();
        outOfSeason.setProject(otherSeason);
        outOfSeason.setDate(LocalDateTime.now().minusDays(3));

        when(projectRepository.findByFreelanceIdAndSeasonId(1L, 9L)).thenReturn(List.of(won, contact));
        when(interviewStepRepository.findByFreelanceIdAndDateBetween(eq(1L), any(), any()))
            .thenReturn(List.of(inSeason, outOfSeason));

        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, 9L);

        assertThat(stats.getTotalProjects()).isEqualTo(2L);
        assertThat(stats.getWonProjects()).isEqualTo(1L);
        assertThat(stats.getLostProjects()).isZero();
        assertThat(stats.getActiveProjects()).isEqualTo(1L);
        assertThat(stats.getAverageDailyRate()).isEqualTo(600.0);
        assertThat(stats.getProjectsByStatus()).containsEntry("WON", 1L).containsEntry("CONTACT", 1L);
        assertThat(stats.getDailyRateRanges())
            .extracting(DashboardStatsDto.DailyRateRange::getLabel, DashboardStatsDto.DailyRateRange::getCount)
            .containsExactly(tuple("500", 1L), tuple("700", 1L));
        // Only the season's step (plus the creation days of its opportunities) feeds the heatmap.
        assertThat(stats.getAverageObtainedDailyRate()).isEqualTo(700);
        assertThat(stats.getActivityHeatmap())
            .extracting(DashboardStatsDto.ActivityDay::getDate)
            .contains(LocalDate.now().minusDays(2))
            .doesNotContain(LocalDate.now().minusDays(3));
        verify(projectRepository, never()).findByFreelanceId(any());
    }

    @Test
    void getDashboardStats_WithNoData_ShouldReturnZeroDefaults() {
        // Given
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of());

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Then
        assertThat(stats.getTotalProjects()).isZero();
        assertThat(stats.getDailyRateRanges()).isEmpty();
        assertThat(stats.getAverageDailyRate()).isZero();
        assertThat(stats.getTargetDailyRate()).isNull();
        assertThat(stats.getAverageObtainedDailyRate()).isNull();
        assertThat(stats.getTotalEstimatedRevenue()).isZero();
        assertThat(stats.getForecastRevenue()).isZero();
        assertThat(stats.getTotalBenchDays()).isZero();
        assertThat(stats.getBenchPeriods()).isZero();
        assertThat(stats.getEstimatedBenchCost()).isZero();
        assertThat(stats.getWonProjects()).isZero();
        assertThat(stats.getLostProjects()).isZero();
        assertThat(stats.getActiveProjects()).isZero();
        assertThat(stats.getConversionFunnel())
            .hasSize(4)
            .allSatisfy(stage -> {
                assertThat(stage.getCount()).isZero();
                assertThat(stage.getConversionRate()).isZero();
            });
    }

    @Test
    void getDashboardStats_ShouldIgnoreRenewalsForNonWonProjects() {
        LocalDate today = LocalDate.now();
        testProject.setStatus(ProjectStatus.INTERVIEW);
        testProject.setStartDate(today.minusMonths(6));
        testProject.setOrderRenewalInMonths(3);
        testFreelance.setNoticePeriodInDays(30);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject));
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));

        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        assertThat(stats.getUpcomingRenewals()).isEmpty();
    }
}
