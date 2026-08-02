package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.StepStatus;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the aggregated dashboard statistics for a freelance: loads the data (projects,
 * contacts, interview steps, freelance settings) and delegates the per-metric computations to
 * {@link DashboardReminders} and {@link DashboardAnalytics}. Kept separate from {@link ProjectService} so the read-only
 * analytics concern stays cohesive and independent of project CRUD.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatsService {

    private final ProjectRepository projectRepository;
    private final FreelanceRepository freelanceRepository;
    private final ContactRepository contactRepository;
    private final InterviewStepRepository interviewStepRepository;

    /** Notice period applied when the freelance has not configured one. */
    private static final int DEFAULT_NOTICE_PERIOD_DAYS = 30;

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats(Long freelanceId) {
        log.debug("Getting dashboard stats for freelance: {}", freelanceId);

        Long totalProjects = projectRepository.countByFreelanceId(freelanceId);
        Double averageDailyRate = projectRepository.findAverageDailyRateByFreelanceId(freelanceId);
        Long wonProjects = projectRepository.countWonByFreelanceId(freelanceId);
        Long lostProjects = projectRepository.countLostByFreelanceId(freelanceId);
        Long activeProjects = projectRepository.countActiveByFreelanceId(freelanceId);

        // Projects by status
        Map<String, Long> projectsByStatus = new LinkedHashMap<>();
        for (ProjectStatus status : ProjectStatus.values()) {
            projectsByStatus.put(status.name(), 0L);
        }
        for (Object[] row : projectRepository.countByFreelanceIdGroupByStatus(freelanceId)) {
            ProjectStatus status = (ProjectStatus) row[0];
            Long count = (Long) row[1];
            projectsByStatus.put(status.name(), count);
        }

        // Projects by work mode
        Map<String, Long> projectsByWorkMode = new LinkedHashMap<>();
        for (WorkMode mode : WorkMode.values()) {
            projectsByWorkMode.put(mode.name(), 0L);
        }
        for (Object[] row : projectRepository.countByFreelanceIdGroupByWorkMode(freelanceId)) {
            WorkMode mode = (WorkMode) row[0];
            Long count = (Long) row[1];
            projectsByWorkMode.put(mode.name(), count);
        }

        List<Project> projects = projectRepository.findByFreelanceId(freelanceId);

        // Lost-reason breakdown (only lost opportunities that carry a reason)
        Map<String, Long> lostReasonsBreakdown = new LinkedHashMap<>();
        for (LostReason reason : LostReason.values()) {
            lostReasonsBreakdown.put(reason.name(), 0L);
        }
        projects.stream()
            .filter(p -> ProjectStatus.LOST.equals(p.getStatus()) && p.getLostReason() != null)
            .forEach(p -> lostReasonsBreakdown.merge(p.getLostReason().name(), 1L, Long::sum));

        int[][] ranges = {{0, 300}, {300, 500}, {500, 700}, {700, 900}, {900, Integer.MAX_VALUE}};
        String[] rangeLabels = {"0-300", "300-500", "500-700", "700-900", "900+"};
        List<DashboardStatsDto.DailyRateRange> dailyRateRanges = new ArrayList<>();
        for (int i = 0; i < ranges.length; i++) {
            int min = ranges[i][0];
            int max = ranges[i][1];
            long count = projects.stream()
                .filter(p -> p.getDailyRate() != null && p.getDailyRate() >= min && p.getDailyRate() < max)
                .count();
            dailyRateRanges.add(DashboardStatsDto.DailyRateRange.builder()
                .label(rangeLabels[i])
                .count(count)
                .build());
        }

        double totalRevenue = projects.stream()
            .filter(p -> p.getTotalRevenue() != null)
            .mapToDouble(Project::getTotalRevenue)
            .sum();
        double forecastRevenue = projects.stream()
            .filter(p -> p.getForecastRevenue() != null)
            .mapToDouble(Project::getForecastRevenue)
            .sum();

        double resolvedAverageRate = averageDailyRate != null ? averageDailyRate : 0;
        long[] bench = DashboardAnalytics.buildBenchStats(projects);

        // Notice period drives how early upcoming contract renewals are surfaced
        int noticePeriodInDays = freelanceRepository.findById(freelanceId)
            .map(Freelance::getNoticePeriodInDays)
            .filter(days -> days > 0)
            .orElse(DEFAULT_NOTICE_PERIOD_DAYS);

        List<Contact> contacts = contactRepository.findByFreelanceId(freelanceId);

        LocalDate today = LocalDate.now();
        List<InterviewStep> validatedSteps =
            interviewStepRepository.findByFreelanceIdAndStatus(freelanceId, StepStatus.VALIDATED);
        List<InterviewStep> heatmapSteps = interviewStepRepository.findByFreelanceIdAndDateBetween(
            freelanceId,
            today.minusDays(DashboardAnalytics.HEATMAP_WINDOW_DAYS).atStartOfDay(),
            today.plusDays(1).atStartOfDay());

        return DashboardStatsDto.builder()
            .totalProjects(totalProjects != null ? totalProjects : 0)
            .averageDailyRate(averageDailyRate != null ? averageDailyRate : 0)
            .totalEstimatedRevenue(totalRevenue)
            .forecastRevenue(forecastRevenue)
            .activeProjects(activeProjects != null ? activeProjects : 0)
            .wonProjects(wonProjects != null ? wonProjects : 0)
            .lostProjects(lostProjects != null ? lostProjects : 0)
            .totalBenchDays(bench[0])
            .benchPeriods(bench[1])
            .estimatedBenchCost(bench[0] * resolvedAverageRate)
            .projectsByStatus(projectsByStatus)
            .projectsByWorkMode(projectsByWorkMode)
            .lostReasonsBreakdown(lostReasonsBreakdown)
            .dailyRateRanges(dailyRateRanges)
            .sourceRoi(DashboardAnalytics.buildSourceRoiRanking(projects))
            .dailyRateEvolution(DashboardAnalytics.buildDailyRateEvolution(projects))
            .conversionFunnel(DashboardAnalytics.buildConversionFunnel(projects))
            .funnelBySource(DashboardAnalytics.buildFunnelBreakdown(projects,
                p -> p.getSource() != null ? p.getSource().getName() : null))
            .funnelByClientType(DashboardAnalytics.buildFunnelBreakdown(projects,
                p -> p.getMiddleman() != null ? "INTERMEDIARY" : "DIRECT"))
            .funnelByEsn(DashboardAnalytics.buildFunnelBreakdown(projects,
                p -> p.getMiddleman() != null ? p.getMiddleman().getCompanyName() : null))
            .missionsEndingSoon(DashboardReminders.buildMissionsEndingSoon(projects, today))
            .staleOpportunities(DashboardReminders.buildStaleOpportunities(projects, LocalDateTime.now()))
            .upcomingRenewals(DashboardReminders.buildUpcomingRenewals(projects, today, noticePeriodInDays))
            .onThisDay(DashboardReminders.buildOnThisDay(projects, contacts, today))
            .dormantContacts(DashboardReminders.buildDormantContacts(contacts, LocalDateTime.now()))
            .skillTrends(DashboardAnalytics.buildSkillTrends(projects))
            .processDurations(DashboardAnalytics.buildProcessDurations(
                projects, DashboardAnalytics.signatureDates(validatedSteps)))
            .activityHeatmap(DashboardAnalytics.buildActivityHeatmap(projects, heatmapSteps, today))
            .build();
    }
}
