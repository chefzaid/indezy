package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardStatsServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private DashboardStatsService dashboardStatsService;

    private Project testProject;
    private Freelance testFreelance;
    private Client testClient;

    @BeforeEach
    void setUp() {
        testFreelance = new Freelance();
        testFreelance.setId(1L);
        testFreelance.setFirstName("John");
        testFreelance.setLastName("Doe");
        testFreelance.setEmail("john.doe@example.com");
        testFreelance.setStatus(EmploymentStatus.FREELANCE);

        testClient = new Client();
        testClient.setId(1L);
        testClient.setCompanyName("Test Company");
        testClient.setCity("Paris");
        testClient.setIsFinal(true);
        testClient.setFreelance(testFreelance);

        testProject = new Project();
        testProject.setId(1L);
        testProject.setRole("Full Stack Developer");
        testProject.setDailyRate(600);
        testProject.setWorkMode(WorkMode.HYBRID);
        testProject.setStartDate(LocalDate.of(2024, 1, 15));
        testProject.setDurationInMonths(6);
        testProject.setDaysPerYear(220);
        testProject.setFreelance(testFreelance);
        testProject.setClient(testClient);
    }

    @Test
    void getDashboardStats_ShouldAggregateCountsRatesAndRanges() {
        // Given
        when(projectRepository.countByFreelanceId(1L)).thenReturn(2L);
        when(projectRepository.findAverageDailyRateByFreelanceId(1L)).thenReturn(600.0);
        when(projectRepository.countWonByFreelanceId(1L)).thenReturn(1L);
        when(projectRepository.countLostByFreelanceId(1L)).thenReturn(0L);
        when(projectRepository.countActiveByFreelanceId(1L)).thenReturn(1L);
        when(projectRepository.countByFreelanceIdGroupByStatus(1L))
                .thenReturn(List.<Object[]>of(new Object[]{ProjectStatus.WON, 1L}));
        when(projectRepository.countByFreelanceIdGroupByWorkMode(1L))
                .thenReturn(List.<Object[]>of(new Object[]{WorkMode.HYBRID, 2L}));
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L);

        // Then
        assertThat(stats.getTotalProjects()).isEqualTo(2L);
        assertThat(stats.getAverageDailyRate()).isEqualTo(600.0);
        assertThat(stats.getWonProjects()).isEqualTo(1L);
        assertThat(stats.getLostProjects()).isZero();
        assertThat(stats.getActiveProjects()).isEqualTo(1L);
        assertThat(stats.getProjectsByStatus().get("WON")).isEqualTo(1L);
        assertThat(stats.getProjectsByWorkMode().get("HYBRID")).isEqualTo(2L);
        // testProject has dailyRate 600 -> falls in the 500-700 bucket
        assertThat(stats.getDailyRateRanges())
                .filteredOn(r -> r.getLabel().equals("500-700"))
                .first()
                .satisfies(r -> assertThat(r.getCount()).isEqualTo(1L));
    }

    @Test
    void getDashboardStats_ShouldBreakDownLostReasons() {
        // Given a lost project with a reason and a non-lost project
        Project lost = new Project();
        lost.setStatus(ProjectStatus.LOST);
        lost.setLostReason(LostReason.RATE_TOO_LOW);
        when(projectRepository.countByFreelanceId(1L)).thenReturn(2L);
        when(projectRepository.findAverageDailyRateByFreelanceId(1L)).thenReturn(600.0);
        when(projectRepository.countWonByFreelanceId(1L)).thenReturn(0L);
        when(projectRepository.countLostByFreelanceId(1L)).thenReturn(1L);
        when(projectRepository.countActiveByFreelanceId(1L)).thenReturn(1L);
        when(projectRepository.countByFreelanceIdGroupByStatus(1L)).thenReturn(List.of());
        when(projectRepository.countByFreelanceIdGroupByWorkMode(1L)).thenReturn(List.of());
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject, lost));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L);

        // Then only the lost project contributes to the breakdown
        assertThat(stats.getLostReasonsBreakdown())
                .containsEntry("RATE_TOO_LOW", 1L)
                .containsEntry("NO_RESPONSE", 0L);
    }

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

        when(projectRepository.countByFreelanceId(1L)).thenReturn(3L);
        when(projectRepository.findAverageDailyRateByFreelanceId(1L)).thenReturn(600.0);
        when(projectRepository.countWonByFreelanceId(1L)).thenReturn(2L);
        when(projectRepository.countLostByFreelanceId(1L)).thenReturn(1L);
        when(projectRepository.countActiveByFreelanceId(1L)).thenReturn(0L);
        when(projectRepository.countByFreelanceIdGroupByStatus(1L)).thenReturn(List.of());
        when(projectRepository.countByFreelanceIdGroupByWorkMode(1L)).thenReturn(List.of());
        when(projectRepository.findByFreelanceId(1L))
                .thenReturn(List.of(linkedinWon, linkedinLost, maltWon));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L);

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

        when(projectRepository.countByFreelanceId(1L)).thenReturn(3L);
        when(projectRepository.findAverageDailyRateByFreelanceId(1L)).thenReturn(680.0);
        when(projectRepository.countWonByFreelanceId(1L)).thenReturn(0L);
        when(projectRepository.countLostByFreelanceId(1L)).thenReturn(0L);
        when(projectRepository.countActiveByFreelanceId(1L)).thenReturn(3L);
        when(projectRepository.countByFreelanceIdGroupByStatus(1L)).thenReturn(List.of());
        when(projectRepository.countByFreelanceIdGroupByWorkMode(1L)).thenReturn(List.of());
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(p2024a, p2024b, p2025));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L);

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

        when(projectRepository.countByFreelanceId(1L)).thenReturn(2L);
        when(projectRepository.findAverageDailyRateByFreelanceId(1L)).thenReturn(550.0);
        when(projectRepository.countWonByFreelanceId(1L)).thenReturn(1L);
        when(projectRepository.countLostByFreelanceId(1L)).thenReturn(0L);
        when(projectRepository.countActiveByFreelanceId(1L)).thenReturn(1L);
        when(projectRepository.countByFreelanceIdGroupByStatus(1L)).thenReturn(List.of());
        when(projectRepository.countByFreelanceIdGroupByWorkMode(1L)).thenReturn(List.of());
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(won, interview));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L);

        // Then: 120,000 * 1.0 + 66,000 * 0.5 = 153,000, while total revenue stays unweighted.
        assertThat(stats.getForecastRevenue()).isEqualTo(153000.0);
        assertThat(stats.getTotalEstimatedRevenue()).isEqualTo(186000.0);
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

        when(projectRepository.countByFreelanceId(1L)).thenReturn(4L);
        when(projectRepository.findAverageDailyRateByFreelanceId(1L)).thenReturn(500.0);
        when(projectRepository.countWonByFreelanceId(1L)).thenReturn(3L);
        when(projectRepository.countLostByFreelanceId(1L)).thenReturn(0L);
        when(projectRepository.countActiveByFreelanceId(1L)).thenReturn(1L);
        when(projectRepository.countByFreelanceIdGroupByStatus(1L)).thenReturn(List.of());
        when(projectRepository.countByFreelanceIdGroupByWorkMode(1L)).thenReturn(List.of());
        when(projectRepository.findByFreelanceId(1L))
                .thenReturn(List.of(first, second, overlapping, pending));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L);

        // Then a single 61-day bench period, costed at the average daily rate.
        assertThat(stats.getTotalBenchDays()).isEqualTo(61L);
        assertThat(stats.getBenchPeriods()).isEqualTo(1L);
        assertThat(stats.getEstimatedBenchCost()).isEqualTo(61 * 500.0);
    }

    @Test
    void getDashboardStats_WithNoData_ShouldReturnZeroDefaults() {
        // Given
        when(projectRepository.countByFreelanceId(1L)).thenReturn(null);
        when(projectRepository.findAverageDailyRateByFreelanceId(1L)).thenReturn(null);
        when(projectRepository.countWonByFreelanceId(1L)).thenReturn(null);
        when(projectRepository.countLostByFreelanceId(1L)).thenReturn(null);
        when(projectRepository.countActiveByFreelanceId(1L)).thenReturn(null);
        when(projectRepository.countByFreelanceIdGroupByStatus(1L)).thenReturn(List.of());
        when(projectRepository.countByFreelanceIdGroupByWorkMode(1L)).thenReturn(List.of());
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of());

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L);

        // Then
        assertThat(stats.getTotalProjects()).isZero();
        assertThat(stats.getAverageDailyRate()).isZero();
        assertThat(stats.getTotalEstimatedRevenue()).isZero();
        assertThat(stats.getForecastRevenue()).isZero();
        assertThat(stats.getTotalBenchDays()).isZero();
        assertThat(stats.getBenchPeriods()).isZero();
        assertThat(stats.getEstimatedBenchCost()).isZero();
        assertThat(stats.getWonProjects()).isZero();
        assertThat(stats.getLostProjects()).isZero();
        assertThat(stats.getActiveProjects()).isZero();
    }
}
