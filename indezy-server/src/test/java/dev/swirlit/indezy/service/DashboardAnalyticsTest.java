package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.StepStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DashboardAnalyticsTest extends DashboardStatsTestSupport {

    @Test
    void getDashboardStats_ShouldRankSourcesBySignedContracts() {
        // Given LinkedIn with 1 won out of 2, and Malt with 1 won out of 1.
        Source linkedin = new Source();
        linkedin.setId(10L);
        linkedin.setName("LinkedIn");
        Source malt = new Source();
        malt.setId(11L);
        malt.setName("Malt");

        Project linkedinWon = new Project();
        linkedinWon.setSource(linkedin);
        linkedinWon.setStatus(ProjectStatus.WON);
        Project linkedinLost = new Project();
        linkedinLost.setSource(linkedin);
        linkedinLost.setStatus(ProjectStatus.LOST);
        Project maltWon = new Project();
        maltWon.setSource(malt);
        maltWon.setStatus(ProjectStatus.WON);

        when(projectRepository.findByFreelanceId(1L))
                .thenReturn(List.of(linkedinWon, linkedinLost, maltWon));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Then both have one signed contract, but Malt's higher conversion rate ranks it first.
        List<DashboardStatsDto.SourceRoi> roi = stats.getSourceRoi();
        assertThat(roi).hasSize(2);
        assertThat(roi.get(0).getSourceName()).isEqualTo("Malt");
        assertThat(roi.get(0).getWonProjects()).isEqualTo(1L);
        assertThat(roi.get(0).getTotalProjects()).isEqualTo(1L);
        assertThat(roi.get(0).getConversionRate()).isEqualTo(100.0);
        assertThat(roi.get(1).getSourceName()).isEqualTo("LinkedIn");
        assertThat(roi.get(1).getTotalProjects()).isEqualTo(2L);
        assertThat(roi.get(1).getConversionRate()).isEqualTo(50.0);
    }

    @Test
    void getDashboardStats_ShouldBuildDailyRateEvolutionByYear() {
        // Given two 2024 projects (asked 600/700, obtained 620/680) and one 2025 project.
        Project p2024a = new Project();
        p2024a.setStartDate(LocalDate.of(2024, 3, 1));
        p2024a.setAskedDailyRate(600);
        p2024a.setDailyRate(620);
        Project p2024b = new Project();
        p2024b.setStartDate(LocalDate.of(2024, 9, 1));
        p2024b.setAskedDailyRate(700);
        p2024b.setDailyRate(680);
        Project p2025 = new Project();
        p2025.setStartDate(LocalDate.of(2025, 1, 1));
        p2025.setDailyRate(750);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(p2024a, p2024b, p2025));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Then years are ordered ascending with averaged rates; 2025 has no asked rate.
        List<DashboardStatsDto.DailyRateEvolution> evolution = stats.getDailyRateEvolution();
        assertThat(evolution).hasSize(2);
        assertThat(evolution.get(0).getPeriod()).isEqualTo("2024");
        assertThat(evolution.get(0).getAverageAskedRate()).isEqualTo(650.0);
        assertThat(evolution.get(0).getAverageObtainedRate()).isEqualTo(650.0);
        assertThat(evolution.get(0).getProjectCount()).isEqualTo(2L);
        assertThat(evolution.get(1).getPeriod()).isEqualTo("2025");
        assertThat(evolution.get(1).getAverageAskedRate()).isZero();
        assertThat(evolution.get(1).getAverageObtainedRate()).isEqualTo(750.0);
    }

    @Test
    void getDashboardStats_ShouldComputeBenchTimeBetweenSignedMissions() {
        // Given two signed missions: Jan-Apr 2024 (3 months) then Jun 2024 (1 month).
        // Bench = 2024-04-01 -> 2024-06-01 = 61 days. A third, overlapping mission adds none.
        Project first = new Project();
        first.setStatus(ProjectStatus.WON);
        first.setStartDate(LocalDate.of(2024, 1, 1));
        first.setDurationInMonths(3);
        Project second = new Project();
        second.setStatus(ProjectStatus.WON);
        second.setStartDate(LocalDate.of(2024, 6, 1));
        second.setDurationInMonths(1);
        Project overlapping = new Project();
        overlapping.setStatus(ProjectStatus.WON);
        overlapping.setStartDate(LocalDate.of(2024, 6, 15));
        overlapping.setDurationInMonths(2);
        // A non-signed project is ignored even though it has dates.
        Project pending = new Project();
        pending.setStatus(ProjectStatus.INTERVIEW);
        pending.setStartDate(LocalDate.of(2025, 1, 1));
        pending.setDurationInMonths(6);

        List.of(first, second, overlapping, pending).forEach(p -> p.setDailyRate(500));
        when(projectRepository.findByFreelanceId(1L))
                .thenReturn(List.of(first, second, overlapping, pending));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Then a single 61-day bench period, costed at the average daily rate.
        assertThat(stats.getTotalBenchDays()).isEqualTo(61L);
        assertThat(stats.getBenchPeriods()).isEqualTo(1L);
        assertThat(stats.getEstimatedBenchCost()).isEqualTo(61 * 500.0);
    }

    @Test
    void getDashboardStats_ShouldBreakDownFunnelBySourceClientTypeAndEsn() {
        // Given sources, an ESN, and four opportunities mixing direct and intermediated deals.
        Source linkedin = new Source();
        linkedin.setName("LinkedIn");
        Source malt = new Source();
        malt.setName("Malt");
        Client esn = new Client();
        esn.setCompanyName("AcmeESN");
        esn.setIsFinal(false);

        Project linkedinWon = new Project();          // direct, LinkedIn, won
        linkedinWon.setSource(linkedin);
        linkedinWon.setStatus(ProjectStatus.WON);
        Project linkedinApplied = new Project();        // direct, LinkedIn, applied
        linkedinApplied.setSource(linkedin);
        linkedinApplied.setStatus(ProjectStatus.CONTACT);
        Project maltViaEsn = new Project();             // intermediated, Malt, interview
        maltViaEsn.setSource(malt);
        maltViaEsn.setMiddleman(esn);
        maltViaEsn.setStatus(ProjectStatus.INTERVIEW);
        Project esnWonNoSource = new Project();          // intermediated, no source, won
        esnWonNoSource.setMiddleman(esn);
        esnWonNoSource.setStatus(ProjectStatus.WON);

        when(projectRepository.findByFreelanceId(1L))
            .thenReturn(List.of(linkedinWon, linkedinApplied, maltViaEsn, esnWonNoSource));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Then by source: groups ordered by name; the source-less project is skipped.
        assertThat(stats.getFunnelBySource())
            .extracting(DashboardStatsDto.FunnelBreakdown::getGroup)
            .containsExactly("LinkedIn", "Malt");
        assertThat(stats.getFunnelBySource().get(0).getStages())
            .extracting(DashboardStatsDto.ConversionFunnelStage::getCount)
            .containsExactly(2L, 1L, 1L, 1L);

        // And by client type: DIRECT (no ESN) vs INTERMEDIARY (through an ESN).
        assertThat(stats.getFunnelByClientType())
            .extracting(DashboardStatsDto.FunnelBreakdown::getGroup)
            .containsExactly("DIRECT", "INTERMEDIARY");
        assertThat(stats.getFunnelByClientType().get(1).getStages())
            .extracting(DashboardStatsDto.ConversionFunnelStage::getCount)
            .containsExactly(2L, 2L, 1L, 1L);

        // And by ESN: only intermediated opportunities are grouped under the ESN name.
        assertThat(stats.getFunnelByEsn())
            .extracting(DashboardStatsDto.FunnelBreakdown::getGroup)
            .containsExactly("AcmeESN");
        assertThat(stats.getFunnelByEsn().get(0).getStages())
            .extracting(DashboardStatsDto.ConversionFunnelStage::getCount)
            .containsExactly(2L, 2L, 1L, 1L);
    }

    @Test
    void getDashboardStats_ShouldRankSkillTrendsByFrequencyWithAverageRate() {
        Project p1 = new Project();
        p1.setId(1L);
        p1.setTechStack("Java, Spring Boot, PostgreSQL");
        p1.setDailyRate(600);
        Project p2 = new Project();
        p2.setId(2L);
        p2.setTechStack("java, Angular");
        p2.setDailyRate(800);
        Project p3 = new Project();
        p3.setId(3L);
        p3.setTechStack("Angular, TypeScript");
        p3.setDailyRate(500);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(p1, p2, p3));

        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        DashboardStatsDto.SkillTrend java = stats.getSkillTrends().stream()
            .filter(t -> t.getSkill().equalsIgnoreCase("java")).findFirst().orElseThrow();
        assertThat(java.getSkill()).isEqualTo("Java");
        assertThat(java.getCount()).isEqualTo(2);
        assertThat(java.getAverageDailyRate()).isEqualTo(700.0);

        DashboardStatsDto.SkillTrend angular = stats.getSkillTrends().stream()
            .filter(t -> t.getSkill().equalsIgnoreCase("angular")).findFirst().orElseThrow();
        assertThat(angular.getCount()).isEqualTo(2);
        assertThat(angular.getAverageDailyRate()).isEqualTo(650.0);

        // The two-occurrence skills rank above the single-occurrence ones.
        assertThat(stats.getSkillTrends().getFirst().getCount()).isEqualTo(2);
    }

    @Test
    void getDashboardStats_ShouldAverageProcessDurationByCounterparty() {
        dev.swirlit.indezy.model.Client middleman = new dev.swirlit.indezy.model.Client();
        middleman.setId(9L);
        middleman.setCompanyName("ESN One");

        Project won = new Project();
        won.setId(50L);
        won.setStatus(ProjectStatus.WON);
        won.setClient(testClient);
        won.setMiddleman(middleman);
        won.setCreatedAt(LocalDateTime.now().minusDays(20));

        InterviewStep validated = new InterviewStep();
        validated.setStatus(StepStatus.VALIDATED);
        validated.setDate(LocalDateTime.now().minusDays(5));
        validated.setProject(won);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(won));
        when(interviewStepRepository.findByFreelanceIdAndStatus(1L, StepStatus.VALIDATED))
            .thenReturn(List.of(validated));

        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Signed 15 days after first contact, grouped under the ESN.
        assertThat(stats.getProcessDurations()).hasSize(1);
        assertThat(stats.getProcessDurations().getFirst().getGroup()).isEqualTo("ESN One");
        assertThat(stats.getProcessDurations().getFirst().getCount()).isEqualTo(1);
        assertThat(stats.getProcessDurations().getFirst().getAverageDays()).isEqualTo(15.0);
    }

    @Test
    void getDashboardStats_ShouldCountDailyProspectionActivity() {
        LocalDateTime now = LocalDateTime.now();
        testProject.setCreatedAt(now);

        InterviewStep step = new InterviewStep();
        step.setStatus(StepStatus.PLANNED);
        step.setDate(now);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject));
        when(interviewStepRepository.findByFreelanceIdAndDateBetween(any(), any(), any()))
            .thenReturn(List.of(step));

        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        LocalDate today = LocalDate.now();
        DashboardStatsDto.ActivityDay todaysActivity = stats.getActivityHeatmap().stream()
            .filter(day -> day.getDate().equals(today)).findFirst().orElseThrow();
        // One opportunity created + one interview step dated today.
        assertThat(todaysActivity.getCount()).isEqualTo(2);
    }
}
