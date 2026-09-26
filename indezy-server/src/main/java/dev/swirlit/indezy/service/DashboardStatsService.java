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

import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

    /**
     * Dashboard statistics of a workspace. With a season, every opportunity-based metric only
     * considers that season's opportunities (and their interview steps); contact reminders stay
     * workspace-wide.
     */
    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats(Long freelanceId, Long seasonId) {
        log.debug("Getting dashboard stats for freelance: {} (season: {})", freelanceId, seasonId);

        List<Project> projects = seasonId != null
            ? projectRepository.findByFreelanceIdAndSeasonId(freelanceId, seasonId)
            : projectRepository.findByFreelanceId(freelanceId);

        long totalProjects = projects.size();
        double averageDailyRate = projects.stream()
            .filter(p -> p.getDailyRate() != null)
            .mapToInt(Project::getDailyRate)
            .average()
            .orElse(0);
        long wonProjects = countWithStatus(projects, ProjectStatus.WON);
        long lostProjects = countWithStatus(projects, ProjectStatus.LOST);
        long activeProjects = totalProjects - wonProjects - lostProjects;

        Map<String, Long> projectsByStatus = countsByEnum(ProjectStatus.values(), projects, Project::getStatus);
        Map<String, Long> projectsByWorkMode = countsByEnum(WorkMode.values(), projects, Project::getWorkMode);

        // Lost-reason breakdown (only lost opportunities that carry a reason)
        Map<String, Long> lostReasonsBreakdown = new LinkedHashMap<>();
        for (LostReason reason : LostReason.values()) {
            lostReasonsBreakdown.put(reason.name(), 0L);
        }
        projects.stream()
            .filter(p -> ProjectStatus.LOST.equals(p.getStatus()) && p.getLostReason() != null)
            .forEach(p -> lostReasonsBreakdown.merge(p.getLostReason().name(), 1L, Long::sum));

        List<DashboardStatsDto.DailyRateRange> dailyRateRanges = buildDailyRateRanges(projects);

        // Unweighted value of the live pipeline and signed missions; lost opportunities earn nothing.
        double totalRevenue = projects.stream()
            .filter(p -> !ProjectStatus.LOST.equals(p.getStatus()))
            .filter(p -> p.getTotalRevenue() != null)
            .mapToDouble(Project::getTotalRevenue)
            .sum();
        double forecastRevenue = projects.stream()
            .filter(p -> p.getForecastRevenue() != null)
            .mapToDouble(Project::getForecastRevenue)
            .sum();

        long[] bench = DashboardAnalytics.buildBenchStats(projects);

        // Notice period drives how early upcoming contract renewals are surfaced
        int noticePeriodInDays = freelanceRepository.findById(freelanceId)
            .map(Freelance::getNoticePeriodInDays)
            .filter(days -> days > 0)
            .orElse(DEFAULT_NOTICE_PERIOD_DAYS);

        List<Contact> contacts = contactRepository.findByFreelanceId(freelanceId);

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<InterviewStep> validatedSteps = inSeason(
            interviewStepRepository.findByFreelanceIdAndStatus(freelanceId, StepStatus.VALIDATED), seasonId);
        List<InterviewStep> heatmapSteps = inSeason(interviewStepRepository.findByFreelanceIdAndDateBetween(
            freelanceId,
            today.minusDays(DashboardAnalytics.HEATMAP_WINDOW_DAYS).atStartOfDay(),
            today.plusDays(1).atStartOfDay()), seasonId);

        return DashboardStatsDto.builder()
            .totalProjects(totalProjects)
            .averageDailyRate(averageDailyRate)
            .totalEstimatedRevenue(totalRevenue)
            .forecastRevenue(forecastRevenue)
            .activeProjects(activeProjects)
            .wonProjects(wonProjects)
            .lostProjects(lostProjects)
            .totalBenchDays(bench[0])
            .benchPeriods(bench[1])
            .estimatedBenchCost(bench[0] * averageDailyRate)
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
            .staleOpportunities(DashboardReminders.buildStaleOpportunities(projects, LocalDateTime.now(ZoneId.systemDefault())))
            .upcomingRenewals(DashboardReminders.buildUpcomingRenewals(projects, today, noticePeriodInDays))
            .onThisDay(DashboardReminders.buildOnThisDay(projects, contacts, today))
            .dormantContacts(DashboardReminders.buildDormantContacts(contacts, LocalDateTime.now(ZoneId.systemDefault())))
            .skillTrends(DashboardAnalytics.buildSkillTrends(projects))
            .processDurations(DashboardAnalytics.buildProcessDurations(
                projects, DashboardAnalytics.signatureDates(validatedSteps)))
            .activityHeatmap(DashboardAnalytics.buildActivityHeatmap(projects, heatmapSteps, today))
            .build();
    }

    private static long countWithStatus(List<Project> projects, ProjectStatus status) {
        return projects.stream().filter(p -> status.equals(p.getStatus())).count();
    }

    /** Counts per enum value (every value present, zero included), ignoring projects without one. */
    private static <E extends Enum<E>> Map<String, Long> countsByEnum(
            E[] values, List<Project> projects, Function<Project, E> classifier) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (E value : values) {
            counts.put(value.name(), 0L);
        }
        for (Project project : projects) {
            E value = classifier.apply(project);
            if (value != null) {
                counts.merge(value.name(), 1L, Long::sum);
            }
        }
        return counts;
    }

    /** Keeps the steps of the season's opportunities; all steps when no season is selected. */
    private static List<InterviewStep> inSeason(List<InterviewStep> steps, Long seasonId) {
        if (seasonId == null) {
            return steps;
        }
        return steps.stream()
            .filter(step -> step.getProject() != null && step.getProject().getSeason() != null
                && seasonId.equals(step.getProject().getSeason().getId()))
            .toList();
    }

    private List<DashboardStatsDto.DailyRateRange> buildDailyRateRanges(List<Project> projects) {
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
        return dailyRateRanges;
    }
}
